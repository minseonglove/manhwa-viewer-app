"""Hayai OCR v2.1 LiteRT adapter for locally re-exported graphs.

Measures .tflite files produced by our own run of the author's `export_kv.py`
(`JustANormalTinkerer/hayai-ocr-v2-tflite`) instead of the published artifacts that
`ocr_eval.models.hayai_litert` reads. It exists to answer one question: does packing
the decoder prefill and decode graphs into a single multi-signature file -- which
drops the ~59 MB of decoder weights the two separate files each carry a copy of --
change what the model reads?

Two modes, chosen by `HAYAI_EXPORT_MODE`:

* `split` -- the author's three-file layout (`hayai_encoder_*.tflite`,
  `hayai_decoder_prefill_*.tflite`, `hayai_decoder_decode_*.tflite`). This is the
  control: same toolchain as `merged`, same host code, unmerged graphs.
* `merged` -- encoder file plus one `hayai_decoder_merged_*.tflite` holding both
  decoder graphs as the signatures `prefill` and `decode`.

`HAYAI_EXPORT_DIR` points at the directory holding those files plus `position_base.npy`
and `tokenizer.json`; `HAYAI_EXPORT_QUANT` selects the quant suffix (default
`wi8_afp32`). Nothing is downloaded -- the artifacts are local by construction.

Both modes run the author's host-side code (`hayai_ocr.litert`): SigLIP2 NaFlex
preprocessing, 2D mRoPE, block-causal masks, greedy loop and detokenization all come
from `LitertOcrEngine`. `merged` only replaces how the two decoder graphs are
constructed and invoked -- `LitertOcrEngine.generate` is inherited unchanged and drives
the signature runners through the same `set_tensor`/`invoke`/`get_tensor` calls it makes
on an `Interpreter`.
"""

from __future__ import annotations

import hashlib
import os
import re
from collections.abc import Callable, Iterable
from importlib.metadata import version
from pathlib import Path

import numpy as np
from hayai_ocr.litert import QUANT_SUFFIX, LitertOcrEngine, normalize_litert_quant
from PIL import Image

SOURCE_MODEL = "JustANormalTinkerer/hayai-ocr-v2"
SOURCE_REVISION = "4cf1398f8d9a56a2d4bd7c1d2fb7648773660a62"  # torch checkpoint snapshot
EXPORT_SCRIPT_REPO = "JustANormalTinkerer/hayai-ocr-v2-tflite"
EXPORT_SCRIPT_REVISION = "f73d1c1b0c9cb53e42aaa8ec8b3625ca3ffc5d21"  # export_kv.py / modeling_hayai.py
DEFAULT_QUANT = "wi8_afp32"
MAX_NEW_TOKENS = 64
REPETITION_PENALTY = 1.0

_INDEXED_NAME = re.compile(r"^(?P<prefix>.*?)(?P<index>\d+)$")


def ordered_signature_names(names: Iterable[str]) -> list[str]:
    """Signature tensor names in the exported module's positional order.

    `get_signature_list` returns names sorted as strings, so `args_10` lands between
    `args_1` and `args_2`; the decode graph takes 29 inputs and returns 25 outputs, and
    feeding them in that order would silently scramble the KV cache.
    """
    parsed = []
    for name in names:
        match = _INDEXED_NAME.match(name)
        if match is None:
            raise ValueError(f"signature tensor name {name!r} has no trailing index")
        parsed.append((match.group("prefix"), int(match.group("index")), name))
    prefixes = {prefix for prefix, _, _ in parsed}
    if len(prefixes) != 1:
        raise ValueError(f"signature tensor names mix prefixes {sorted(prefixes)}")
    indices = sorted(index for _, index, _ in parsed)
    if indices != list(range(len(parsed))):
        raise ValueError(f"signature tensor indices are not 0..{len(parsed) - 1}: {indices}")
    return [name for _, _, name in sorted(parsed, key=lambda item: item[1])]


class _SignatureGraph:
    """Interpreter-shaped view of one signature, so `LitertOcrEngine.generate` can drive it."""

    def __init__(self, runner):
        self._runner = runner
        self._inputs: dict[str, np.ndarray] = {}
        self._outputs: dict[str, np.ndarray] = {}
        self._input_names = ordered_signature_names(runner.get_input_details().keys())
        self._output_names = ordered_signature_names(runner.get_output_details().keys())

    def get_input_details(self) -> list[dict]:
        return [{"index": name} for name in self._input_names]

    def get_output_details(self) -> list[dict]:
        return [{"index": name} for name in self._output_names]

    def set_tensor(self, name: str, value: np.ndarray) -> None:
        self._inputs[name] = value

    def invoke(self) -> None:
        self._outputs = self._runner(**self._inputs)

    def get_tensor(self, name: str) -> np.ndarray:
        return self._outputs[name]


class _MergedDecoderEngine(LitertOcrEngine):
    """Engine whose prefill and decode graphs are two signatures of one .tflite file.

    `LitertOcrEngine.__init__` resolves exactly three files by filename and builds one
    interpreter per file, so construction is replaced here. Everything downstream --
    preprocessing, RoPE, masks, the greedy loop -- is inherited.
    """

    def __init__(self, model_dir: Path, encoder_path: Path, decoder_path: Path):
        import ai_edge_litert.interpreter as litert
        from tokenizers import Tokenizer

        self.encoder_path = encoder_path
        self.prefill_path = decoder_path
        self.decode_path = decoder_path
        self._litert = litert

        self.position_base = np.load(str(model_dir / "position_base.npy"))
        self.tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
        self.bos_id = self.tokenizer.token_to_id("<bos>")
        self.eos_id = self.tokenizer.token_to_id("<eos>")
        self.pad_id = self.tokenizer.token_to_id("<pad>")
        if self.bos_id is None:
            self.bos_id = 1
        if self.eos_id is None:
            self.eos_id = 2
        if self.pad_id is None:
            self.pad_id = self.eos_id

        self.enc = litert.Interpreter(model_path=str(encoder_path))
        self.enc.allocate_tensors()
        self._decoder = litert.Interpreter(model_path=str(decoder_path))
        signatures = set(self._decoder.get_signature_list())
        if signatures != {"prefill", "decode"}:
            raise RuntimeError(f"{decoder_path.name} has signatures {sorted(signatures)}, expected prefill + decode")
        self.prefill = _SignatureGraph(self._decoder.get_signature_runner("prefill"))
        self.decode = _SignatureGraph(self._decoder.get_signature_runner("decode"))

        self._enc_in = [d["index"] for d in self.enc.get_input_details()]
        self._enc_out = [d["index"] for d in self.enc.get_output_details()]
        self._pre_in = [d["index"] for d in self.prefill.get_input_details()]
        self._pre_out = [d["index"] for d in self.prefill.get_output_details()]
        self._dec_in = [d["index"] for d in self.decode.get_input_details()]
        self._dec_out = [d["index"] for d in self.decode.get_output_details()]


def _artifact_sha256(paths: Iterable[Path]) -> dict[str, str]:
    """Full 64-char digests, unlike `hayai_litert`'s 16-char truncation -- provenance is the point of this run."""
    return {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    if device == "mps":
        raise RuntimeError("the LiteRT backend has no Metal/MPS path on macOS; run with --device cpu or auto")

    export_dir = os.environ.get("HAYAI_EXPORT_DIR")
    if not export_dir:
        raise RuntimeError("set HAYAI_EXPORT_DIR to the directory holding the exported .tflite files")
    model_dir = Path(export_dir)
    mode = os.environ.get("HAYAI_EXPORT_MODE", "split")
    if mode not in ("split", "merged"):
        raise RuntimeError(f"HAYAI_EXPORT_MODE must be 'split' or 'merged', got {mode!r}")
    quant = normalize_litert_quant(os.environ.get("HAYAI_EXPORT_QUANT", DEFAULT_QUANT))
    suffix = QUANT_SUFFIX[quant]

    encoder_path = model_dir / f"hayai_encoder_{suffix}.tflite"
    decoder_path = model_dir / f"hayai_decoder_merged_{suffix}.tflite"
    if mode == "split":
        artifacts = [
            encoder_path,
            model_dir / f"hayai_decoder_prefill_{suffix}.tflite",
            model_dir / f"hayai_decoder_decode_{suffix}.tflite",
        ]
        graphs = "encoder + decoder prefill + decoder decode, three separate files"
    else:
        artifacts = [encoder_path, decoder_path]
        graphs = "encoder + one decoder file carrying the prefill and decode signatures"

    required = [*artifacts, model_dir / "position_base.npy", model_dir / "tokenizer.json"]
    missing = [path.name for path in required if not path.is_file()]
    if missing:
        raise RuntimeError(f"HAYAI_EXPORT_DIR={model_dir} is missing {missing} for mode {mode}")

    if mode == "split":
        engine = LitertOcrEngine(quant=quant, model_dir=model_dir)
    else:
        engine = _MergedDecoderEngine(model_dir, encoder_path, decoder_path)

    def recognize(image: Image.Image) -> str:
        return engine.generate(image, max_new_tokens=MAX_NEW_TOKENS, repetition_penalty=REPETITION_PENALTY)

    recognize(Image.new("RGB", (64, 64), "white"))

    info = {
        "model": SOURCE_MODEL,
        "revision": SOURCE_REVISION,
        "export_script_repo": EXPORT_SCRIPT_REPO,
        "export_script_revision": EXPORT_SCRIPT_REVISION,
        "device": "mac-cpu-litert",
        "quant": quant,
        "export_mode": mode,
        "export_dir": str(model_dir),
        "artifact_sha256": _artifact_sha256(artifacts),
        "settings": (
            f"locally re-exported LiteRT graphs, quant={quant}, mode={mode} ({graphs}); "
            f"hayai-ocr {version('hayai-ocr')} LitertOcrEngine host code via "
            f"ai-edge-litert {version('ai-edge-litert')} (CPU, default XNNPACK, default threads); "
            "fixed shapes MAX_PATCHES=256 / MAX_TEXT=64; SigLIP2 NaFlex preprocessing, 2D mRoPE and "
            "block-causal masks computed host-side in numpy; greedy argmax, "
            f"repetition_penalty={REPETITION_PENALTY}, max_new_tokens={MAX_NEW_TOKENS}, weight-only "
            "int8 weights with fp32 activations, output = tokenizer decode with skip_special_tokens "
            "(no extra post-processing)"
        ),
    }
    return recognize, info
