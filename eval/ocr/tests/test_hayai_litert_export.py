import sys
import types

import numpy as np
import pytest
import tokenizers

from ocr_eval.models.hayai_litert_export import (
    DEFAULT_QUANT,
    QUANT_SUFFIX,
    _MergedDecoderEngine,
    _SignatureGraph,
    load,
    ordered_signature_names,
)


def test_orders_by_trailing_index_not_lexicographically():
    names = sorted(f"args_{i}" for i in range(12))
    assert names[2] == "args_10"  # what get_signature_list hands back
    assert ordered_signature_names(names) == [f"args_{i}" for i in range(12)]


def test_single_name_is_returned_unchanged():
    assert ordered_signature_names(["output_0"]) == ["output_0"]


def test_rejects_name_without_trailing_index():
    with pytest.raises(ValueError, match="trailing index"):
        ordered_signature_names(["args_0", "visual_features"])


def test_rejects_mixed_prefixes():
    with pytest.raises(ValueError, match="mix prefixes"):
        ordered_signature_names(["args_0", "output_1"])


def test_rejects_gap_in_indices():
    with pytest.raises(ValueError, match="not 0.."):
        ordered_signature_names(["args_0", "args_2"])


def test_rejects_duplicate_indices():
    with pytest.raises(ValueError, match="not 0.."):
        ordered_signature_names(["args_0", "args_0"])


# --- _SignatureGraph -------------------------------------------------------


class _FakeSignatureRunner:
    """Stands in for ai_edge_litert's SignatureRunner: the real one is called with
    named kwargs and returns a dict of named outputs. This fake records exactly
    what it received and returns caller-supplied canned outputs, so a test can
    check inbound and outbound routing independently, without a real .tflite."""

    def __init__(self, input_names, output_names, outputs_by_name):
        self._input_details = {name: {} for name in input_names}
        self._output_details = {name: {} for name in output_names}
        self._outputs_by_name = outputs_by_name
        self.received = None

    def get_input_details(self):
        return self._input_details

    def get_output_details(self):
        return self._output_details

    def __call__(self, **kwargs):
        self.received = dict(kwargs)
        return dict(self._outputs_by_name)


def test_signature_graph_get_details_use_index_key_like_the_interpreter_api():
    runner = _FakeSignatureRunner(["args_1", "args_0"], ["args_0"], {"args_0": np.zeros(1)})
    graph = _SignatureGraph(runner)

    assert graph.get_input_details() == [{"index": "args_0"}, {"index": "args_1"}]
    assert graph.get_output_details() == [{"index": "args_0"}]


def test_signature_graph_round_trips_kv_cache_tensors_despite_scrambled_detail_order():
    """29 inputs / 25 outputs mirrors the decode signature's real shape (see this
    module's docstring); 24 of each are KV-cache tensors -- exactly what a routing
    bug in the shim would scramble."""
    canonical_inputs = [f"args_{i}" for i in range(29)]
    canonical_outputs = [f"args_{i}" for i in range(25)]
    # Lexicographic order is what get_signature_list actually returns (args_10
    # before args_2); scramble the outputs a different way (reversed) to prove
    # the fix isn't specific to string sorting.
    scrambled_inputs = sorted(canonical_inputs)
    scrambled_outputs = list(reversed(canonical_outputs))

    input_values = {name: np.array([i]) for i, name in enumerate(canonical_inputs)}
    output_values = {name: np.array([100 + i]) for i, name in enumerate(canonical_outputs)}

    runner = _FakeSignatureRunner(scrambled_inputs, scrambled_outputs, output_values)
    graph = _SignatureGraph(runner)

    assert [d["index"] for d in graph.get_input_details()] == canonical_inputs
    assert [d["index"] for d in graph.get_output_details()] == canonical_outputs

    for name, value in input_values.items():
        graph.set_tensor(name, value)
    graph.invoke()

    assert runner.received.keys() == set(canonical_inputs)
    for name, value in input_values.items():
        assert runner.received[name] is value

    for name, expected in output_values.items():
        assert graph.get_tensor(name) is expected


# --- _MergedDecoderEngine signature-set validation -------------------------


class _FakeDecoderInterpreter:
    """Stands in for the ai_edge_litert Interpreter built from the merged decoder
    .tflite -- only the two calls _MergedDecoderEngine.__init__ makes on it."""

    def __init__(self, signatures, runners=None):
        self._signatures = signatures
        self._runners = runners or {}

    def get_signature_list(self):
        return self._signatures

    def get_signature_runner(self, name):
        return self._runners[name]


class _FakeTokenizer:
    """Stands in for tokenizers.Tokenizer so the test doesn't need a real tokenizer.json."""

    def token_to_id(self, token):
        return {"<bos>": 1, "<eos>": 2, "<pad>": 0}[token]

    @classmethod
    def from_file(cls, path):
        return cls()


def _install_fake_ai_edge_litert(monkeypatch, decoder_interpreter):
    """ai_edge_litert is not installed in this venv; fake sys.modules so the
    `import ai_edge_litert.interpreter as litert` inside _MergedDecoderEngine.__init__
    resolves without a real .tflite file. __init__ builds the encoder Interpreter
    first and the decoder second, so the second call is the one under test."""

    class _FakeEncoderInterpreter:
        def allocate_tensors(self):
            pass

        def get_input_details(self):
            return [{"index": 0}, {"index": 1}, {"index": 2}]

        def get_output_details(self):
            return [{"index": 0}]

    calls = {"n": 0}

    def _interpreter(*, model_path, **kwargs):
        calls["n"] += 1
        return _FakeEncoderInterpreter() if calls["n"] == 1 else decoder_interpreter

    fake_interpreter_module = types.ModuleType("ai_edge_litert.interpreter")
    fake_interpreter_module.Interpreter = _interpreter
    fake_package = types.ModuleType("ai_edge_litert")
    fake_package.interpreter = fake_interpreter_module

    monkeypatch.setitem(sys.modules, "ai_edge_litert", fake_package)
    monkeypatch.setitem(sys.modules, "ai_edge_litert.interpreter", fake_interpreter_module)


def _write_position_base(model_dir):
    np.save(model_dir / "position_base.npy", np.zeros((16, 16, 768), dtype=np.float32))


@pytest.mark.parametrize("signatures", [["decode", "extra"], ["prefill"], []])
def test_merged_decoder_engine_rejects_bad_signature_sets(tmp_path, monkeypatch, signatures):
    monkeypatch.setattr(tokenizers, "Tokenizer", _FakeTokenizer)
    _write_position_base(tmp_path)
    _install_fake_ai_edge_litert(monkeypatch, _FakeDecoderInterpreter(signatures=signatures))

    with pytest.raises(RuntimeError, match=r"expected prefill \+ decode"):
        _MergedDecoderEngine(tmp_path, tmp_path / "encoder.tflite", tmp_path / "decoder.tflite")


def test_merged_decoder_engine_builds_signature_graphs_for_valid_signature_set(tmp_path, monkeypatch):
    monkeypatch.setattr(tokenizers, "Tokenizer", _FakeTokenizer)
    _write_position_base(tmp_path)

    prefill_runner = _FakeSignatureRunner(["args_1", "args_0"], ["args_0"], {"args_0": np.zeros(1)})
    decode_runner = _FakeSignatureRunner(["args_0"], ["args_0"], {"args_0": np.zeros(1)})
    decoder = _FakeDecoderInterpreter(
        signatures=["prefill", "decode"],
        runners={"prefill": prefill_runner, "decode": decode_runner},
    )
    _install_fake_ai_edge_litert(monkeypatch, decoder)

    engine = _MergedDecoderEngine(tmp_path, tmp_path / "encoder.tflite", tmp_path / "decoder.tflite")

    assert isinstance(engine.prefill, _SignatureGraph)
    assert isinstance(engine.decode, _SignatureGraph)
    assert engine._pre_in == ["args_0", "args_1"]
    assert engine._dec_in == ["args_0"]
    # Unlike the base class, this engine never reads quant/quant_dir -- they must stay gone.
    assert not hasattr(engine, "quant")
    assert not hasattr(engine, "quant_dir")


# --- load(): missing-artifact pre-check ------------------------------------


def _write_stub_artifacts(model_dir, suffix, mode):
    (model_dir / f"hayai_encoder_{suffix}.tflite").write_bytes(b"")
    if mode == "split":
        (model_dir / f"hayai_decoder_prefill_{suffix}.tflite").write_bytes(b"")
        (model_dir / f"hayai_decoder_decode_{suffix}.tflite").write_bytes(b"")
    else:
        (model_dir / f"hayai_decoder_merged_{suffix}.tflite").write_bytes(b"")


@pytest.mark.parametrize("mode", ["split", "merged"])
def test_load_reports_missing_position_base_and_tokenizer_before_touching_the_model(tmp_path, monkeypatch, mode):
    suffix = QUANT_SUFFIX[DEFAULT_QUANT]
    _write_stub_artifacts(tmp_path, suffix, mode)
    # position_base.npy and tokenizer.json deliberately absent.

    monkeypatch.setenv("HAYAI_EXPORT_DIR", str(tmp_path))
    monkeypatch.setenv("HAYAI_EXPORT_MODE", mode)
    monkeypatch.delenv("HAYAI_EXPORT_QUANT", raising=False)

    with pytest.raises(RuntimeError) as exc_info:
        load("cpu")

    message = str(exc_info.value)
    assert "position_base.npy" in message
    assert "tokenizer.json" in message
