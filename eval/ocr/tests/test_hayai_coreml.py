import ast
import inspect
import textwrap
from types import SimpleNamespace

import numpy as np
import pytest

# The adapter drives coremltools on top of the author's LiteRT host code; all
# three are absent from `.venv` and present in the venv the CoreML run uses.
pytest.importorskip("coremltools")
pytest.importorskip("hayai_ocr")
pytest.importorskip("tokenizers")

import coremltools as ct  # noqa: E402
from hayai_ocr.litert import LitertOcrEngine  # noqa: E402

from ocr_eval.models.hayai_coreml import (  # noqa: E402
    CACHE_OUTPUTS, DECODE_CACHE_INPUTS, DECODE_POS_INPUT, DECODE_SCALAR_INPUTS,
    ENCODER_INPUTS, TOTAL, CoreMLOcrEngine, _CoreMLGraph, _check_io_order,
    _DecodeGraph, _declared_inputs, _lut_codes, _mil_op_counts, _palette_bits,
    pos_to_write_mask,
)

FLOAT32, FLOAT16, INT32 = 65568, 65552, 131104


def test_write_mask_used_as_a_where_condition_selects_only_slot_pos():
    mask = pos_to_write_mask(257)
    assert mask.shape == (1, 1, TOTAL, 1)
    assert mask.dtype == np.bool_
    written = np.where(mask, np.full((1, 2, TOTAL, 64), 9.0), np.zeros((1, 2, TOTAL, 64)))
    assert (written[:, :, 257, :] == 9.0).all()
    assert (np.delete(written, 257, axis=2) == 0.0).all()


def test_write_mask_matches_the_in_graph_comparison_it_replaces():
    """export_kv.DecodeDecoderKV builds `arange(320) == pos`; this must be that."""
    for pos in (0, 256, 257, 300, TOTAL - 1):
        expected = (np.arange(TOTAL) == pos).reshape(1, 1, TOTAL, 1)
        assert np.array_equal(pos_to_write_mask(pos), expected)


@pytest.mark.parametrize("pos", [-1, TOTAL, TOTAL + 1])
def test_write_mask_refuses_a_pos_that_names_no_slot(pos):
    with pytest.raises(ValueError, match="outside the cache"):
        pos_to_write_mask(pos)


def _decode_graph(dtypes):
    graph = object.__new__(_DecodeGraph)
    graph._names = [f"in_{i}" for i in range(len(dtypes))]
    graph._dtypes = dtypes
    graph._outputs = []
    graph._feed = {}
    graph._result = {}
    return graph


def test_decode_expands_the_pos_scalar_generate_passes():
    graph = _decode_graph([np.int32] * DECODE_POS_INPUT + [np.float32])
    graph.set_tensor(DECODE_POS_INPUT, np.array(257, dtype=np.int64))
    written = graph._feed[f"in_{DECODE_POS_INPUT}"]
    assert written.shape == (1, 1, TOTAL, 1)
    assert written.dtype == np.float32
    assert np.array_equal(written, pos_to_write_mask(257).astype(np.float32))


def test_other_decode_inputs_pass_through_and_take_the_graphs_dtype():
    graph = _decode_graph([np.int32, np.float32])
    graph.set_tensor(0, np.array([[7]], dtype=np.int64))
    graph.set_tensor(1, np.zeros((1, 1, 32), dtype=np.float64))
    assert graph._feed["in_0"].dtype == np.int32
    assert graph._feed["in_0"].tolist() == [[7]]
    assert graph._feed["in_1"].dtype == np.float32


def test_non_decode_graphs_leave_every_index_alone():
    n = DECODE_POS_INPUT + 2
    graph = object.__new__(_CoreMLGraph)
    graph._names = [f"in_{i}" for i in range(n)]
    graph._dtypes = [np.float32] * n
    graph._outputs = []
    graph._feed = {}
    for i in range(n):
        graph.set_tensor(i, np.array(257, dtype=np.int64))
    for i in range(n):
        assert graph._feed[f"in_{i}"].shape == ()
        assert graph._feed[f"in_{i}"].item() == 257.0


# --- the IO order `generate` addresses positionally -------------------------

def _good_io():
    out = ["logits", *CACHE_OUTPUTS]
    return {
        "encoder": {"inputs": list(ENCODER_INPUTS), "outputs": ["visual_features"]},
        "prefill": {"inputs": ["visual_features", "text_token_ids", "cos", "sin", "mask"], "outputs": out},
        "decode": {"inputs": [*DECODE_SCALAR_INPUTS, *DECODE_CACHE_INPUTS], "outputs": out},
    }


def test_io_order_check_accepts_the_graphs_convert_py_emits():
    _check_io_order(_good_io())


def test_io_order_check_rejects_reordered_caches():
    """The 24 caches share one shape, so only the names can catch a swap."""
    io = _good_io()
    io["decode"]["inputs"][5], io["decode"]["inputs"][6] = io["decode"]["inputs"][6], io["decode"]["inputs"][5]
    with pytest.raises(ValueError, match="decode inputs"):
        _check_io_order(io)


def test_io_order_check_rejects_k_and_v_halves_swapped():
    io = _good_io()
    half = len(CACHE_OUTPUTS) // 2
    io["prefill"]["outputs"] = ["logits", *CACHE_OUTPUTS[half:], *CACHE_OUTPUTS[:half]]
    with pytest.raises(ValueError, match="prefill outputs"):
        _check_io_order(io)


def test_io_order_check_rejects_a_renamed_encoder_input():
    io = _good_io()
    io["encoder"]["inputs"][1] = "pixel_attention_mask"
    with pytest.raises(ValueError, match="encoder inputs"):
        _check_io_order(io)


def test_io_order_check_rejects_decode_outputs_that_do_not_feed_back_in_order():
    """generate feeds decode's outputs straight back into decode's cache inputs."""
    io = _good_io()
    io["decode"]["outputs"] = ["logits", *reversed(CACHE_OUTPUTS)]
    with pytest.raises(ValueError, match="decode outputs"):
        _check_io_order(io)


def test_io_order_check_rejects_a_cache_count_mismatch():
    io = _good_io()
    io["prefill"]["outputs"] = io["prefill"]["outputs"][:-1]
    io["decode"]["outputs"] = io["decode"]["outputs"][:-1]
    with pytest.raises(ValueError, match="prefill outputs"):
        _check_io_order(io)


# --- dtype derivation and provenance, against a stub spec -------------------

def _tensor(extents):
    return SimpleNamespace(type=SimpleNamespace(tensorType=SimpleNamespace(
        dimensions=[SimpleNamespace(constant=SimpleNamespace(size=n)) for n in extents])))


def _stub_op(type_name, lut_extents, opset_style):
    """A stub op. `lut_extents` shapes the LUT the way `opset_style`'s opset stores it.

    iOS16 puts the LUT in `attributes`, rank 1. iOS18 puts it in `inputs` as an
    immediate, rank 4 `[groups, 1, codes, vector]`, and leaves `attributes` without it.
    """
    if type_name.rsplit(".", 1)[-1] != "constexpr_lut_to_dense":
        return SimpleNamespace(type=type_name)
    if opset_style == "ios16":
        return SimpleNamespace(type=type_name, attributes={"lut": _tensor(lut_extents)})
    return SimpleNamespace(
        type=type_name,
        attributes={"name": _tensor([1])},
        inputs={"lut": SimpleNamespace(
            arguments=[SimpleNamespace(value=_tensor(lut_extents))])},
    )


def _stub_spec(inputs, ops=("const", "const", "constexpr_lut_to_dense"), version=7,
               lut_entries=64, opset_style="ios16", lut_extents=None):
    extents = lut_extents if lut_extents is not None else [lut_entries]
    block = SimpleNamespace(operations=[_stub_op(t, extents, opset_style) for t in ops])
    return SimpleNamespace(
        specificationVersion=version,
        description=SimpleNamespace(input=[
            SimpleNamespace(name=n, type=SimpleNamespace(multiArrayType=SimpleNamespace(dataType=c)))
            for n, c in inputs
        ]),
        mlProgram=SimpleNamespace(functions={
            "main": SimpleNamespace(opset="CoreML6", block_specializations={
                "CoreML6": block,
            })
        }),
    )


def _graph_with_spec(monkeypatch, spec, names):
    monkeypatch.setattr(ct.models, "MLModel", lambda *a, **k: SimpleNamespace(get_spec=lambda: spec))
    return _CoreMLGraph(__file__, {"inputs": names, "outputs": ["logits"]}, ct.ComputeUnit.CPU_ONLY)


def test_graph_takes_each_dtype_from_the_input_the_name_resolves_to(monkeypatch):
    spec = _stub_spec([("a", INT32), ("b", FLOAT16), ("c", FLOAT32)])
    graph = _graph_with_spec(monkeypatch, spec, ["c", "a", "b"])
    assert graph._dtypes == [np.float32, np.int32, np.float16]


def test_graph_refuses_a_dtype_the_shim_cannot_cast_to(monkeypatch):
    spec = _stub_spec([("a", 131072)])  # INT64, which no graph declares
    with pytest.raises(ValueError, match="ArrayDataType 131072"):
        _graph_with_spec(monkeypatch, spec, ["a"])


def test_graph_records_the_spec_version_and_compression_ops_it_was_built_from(monkeypatch):
    spec = _stub_spec([("a", FLOAT32)], ops=["const"] * 3 + ["constexpr_lut_to_dense"] * 2 + ["linear"])
    graph = _graph_with_spec(monkeypatch, spec, ["a"])
    assert graph.spec_version == 7
    assert graph.compression_ops == {"constexpr_lut_to_dense": 2}


def test_compression_ops_still_counts_opset_prefixed_names(monkeypatch):
    """A compiled graph names ops `ios16.constexpr_*`; reading 0 would say fp16."""
    spec = _stub_spec([("a", FLOAT32)], ops=["const", "ios16.constexpr_affine_dequantize", "ios16.linear"])
    graph = _graph_with_spec(monkeypatch, spec, ["a"])
    assert graph.compression_ops == {"ios16.constexpr_affine_dequantize": 1}


def test_mil_op_counts_reads_the_specialization_the_function_declares():
    spec = _stub_spec([], ops=["const", "linear", "const"])
    assert _mil_op_counts(spec) == {"const": 2, "linear": 1}


@pytest.mark.parametrize("entries, bits", [(64, 6), (16, 4), (256, 8), (2, 1)])
def test_palette_bits_reads_the_width_off_the_lut_length(entries, bits):
    spec = _stub_spec([], ops=["constexpr_lut_to_dense"], lut_entries=entries)
    assert _palette_bits(spec) == [bits]


@pytest.mark.parametrize("ops", [
    ["const", "linear"],                              # fp16: no compression at all
    ["const", "constexpr_affine_dequantize"],         # int8 linear: compressed, no LUT
])
def test_palette_bits_is_empty_when_no_lut_is_present(ops):
    assert _palette_bits(_stub_spec([], ops=ops)) == []


def test_palette_bits_still_reads_opset_prefixed_names():
    spec = _stub_spec([], ops=["ios16.constexpr_lut_to_dense"], lut_entries=16)
    assert _palette_bits(spec) == [4]


def test_palette_bits_refuses_a_lut_length_that_is_not_a_power_of_two():
    spec = _stub_spec([], ops=["constexpr_lut_to_dense"], lut_entries=48)
    with pytest.raises(ValueError, match="48-code LUT"):
        _palette_bits(spec)


# --- iOS18 stores the same op's LUT somewhere else -------------------------
# The first cut of this read only the iOS16 attribute and raised on every iOS18
# graph, which the iOS16-shaped stubs above could not have caught.

@pytest.mark.parametrize("extents, bits", [
    ([1, 1, 64, 1], 6),     # per-tensor 6-bit: one group
    ([1, 1, 16, 1], 4),     # per-tensor 4-bit
    ([48, 1, 16, 1], 4),    # grouped-channel 4-bit: 48 groups, still 4 bits
    ([48, 1, 64, 1], 6),    # grouped-channel 6-bit
])
def test_palette_bits_reads_the_ios18_lut_from_the_op_input(extents, bits):
    spec = _stub_spec([], ops=["constexpr_lut_to_dense"],
                      opset_style="ios18", lut_extents=extents)
    assert _palette_bits(spec) == [bits]


# --- merged mode: prefill and decode as two functions of one package ---------

def _multifunction_spec(functions):
    """A spec shaped like a merged package: `description.input` empty, IO per function."""
    return SimpleNamespace(
        specificationVersion=9,
        description=SimpleNamespace(
            input=[],
            functions=[
                SimpleNamespace(name=name, input=[
                    SimpleNamespace(name=n, type=SimpleNamespace(
                        multiArrayType=SimpleNamespace(dataType=c)))
                    for n, c in inputs])
                for name, inputs in functions.items()
            ],
        ),
        mlProgram=SimpleNamespace(functions={
            "main": SimpleNamespace(opset="CoreML8", block_specializations={
                "CoreML8": SimpleNamespace(operations=[]),
            })
        }),
    )


def test_declared_inputs_reads_the_named_functions_own_io():
    spec = _multifunction_spec({"prefill": [("visual_features", FLOAT32)],
                                "decode": [("token_ids", INT32), ("cos", FLOAT16)]})
    assert [i.name for i in _declared_inputs(spec, "decode")] == ["token_ids", "cos"]
    assert [i.name for i in _declared_inputs(spec, "prefill")] == ["visual_features"]


def test_declared_inputs_on_a_merged_spec_without_a_function_name_finds_nothing():
    """The top level is empty on a merged package — the reason the lookup takes a name."""
    spec = _multifunction_spec({"prefill": [("visual_features", FLOAT32)]})
    assert list(_declared_inputs(spec, None)) == []


def test_declared_inputs_names_the_functions_it_does_have():
    spec = _multifunction_spec({"prefill": [], "decode": []})
    with pytest.raises(ValueError, match=r"no function 'decoed'.*\['prefill', 'decode'\]"):
        _declared_inputs(spec, "decoed")


def test_declared_inputs_still_reads_the_top_level_for_a_single_function_model():
    spec = _stub_spec([("a", INT32), ("b", FLOAT16)])
    assert [i.name for i in _declared_inputs(spec, None)] == ["a", "b"]


def test_ios18_grouping_does_not_leak_into_the_width():
    """`[48,1,16,1]` holds 48*16 elements but only 16 codes; counting elements reads 9-bit."""
    assert _lut_codes(_stub_op("constexpr_lut_to_dense", [48, 1, 16, 1], "ios18")) == 16
    assert _lut_codes(_stub_op("constexpr_lut_to_dense", [64], "ios16")) == 64


def test_palette_bits_separates_two_recipes_one_op_histogram_cannot(monkeypatch):
    """palette6 and palette4 carry the same counts; the width is the only tell."""
    ops = ["constexpr_lut_to_dense"] * 73
    six = _graph_with_spec(monkeypatch, _stub_spec([("a", FLOAT32)], ops=ops, lut_entries=64), ["a"])
    four = _graph_with_spec(monkeypatch, _stub_spec([("a", FLOAT32)], ops=ops, lut_entries=16), ["a"])
    assert six.compression_ops == four.compression_ops == {"constexpr_lut_to_dense": 73}
    assert six.palette_bits == [6]
    assert four.palette_bits == [4]


# --- the module's central claim: `generate` is inherited verbatim -----------

def _self_attributes(func, ctx):
    tree = ast.parse(textwrap.dedent(inspect.getsource(func)))
    return {
        node.attr for node in ast.walk(tree)
        if isinstance(node, ast.Attribute) and isinstance(node.ctx, ctx)
        and isinstance(node.value, ast.Name) and node.value.id == "self"
    }


def test_init_assigns_every_attribute_the_inherited_generate_reads():
    read = _self_attributes(LitertOcrEngine.generate, ast.Load)
    assigned = _self_attributes(CoreMLOcrEngine.__init__, ast.Store)
    assert read, "failed to parse generate's attribute reads"
    assert read <= assigned, f"generate reads {sorted(read - assigned)}, which __init__ never assigns"
