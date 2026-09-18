"""Hayai OCR v2.1 CoreML adapter (graphs converted from the torch checkpoint).

Measures what an iOS build would actually run. The three KV-cache graphs the
model author exports for LiteRT (SigLIP2 encoder, decoder prefill, decoder
decode step) are converted straight from the pinned torch checkpoint to CoreML
mlprograms, and executed through `coremltools`.

Everything outside graph execution stays on the author's official path: this
module subclasses `hayai_ocr.litert.LitertOcrEngine` and inherits `generate`
*verbatim*, so SigLIP2 NaFlex preprocessing, position-embedding resize, 2D
mRoPE, the block-causal / decode masks, the greedy loop and the tokenizer decode
are the same code the measured LiteRT run used. Only the objects `generate`
calls `set_tensor`/`invoke`/`get_tensor` on are swapped, by `_CoreMLGraph`.

Two adaptations live in that shim, and nowhere else:

* The exported decode graph takes a host-computed one-hot `write_mask` where the
  LiteRT graph takes a `pos` scalar. Deriving the cache slot in-graph needs
  `arange == pos`, whose index arithmetic lowers to `aten::floor_divide` on a
  symbolic int and fails conversion. `generate` still passes `pos`; the shim
  expands it. Same value, computed one step earlier.
* CoreML declares int32 inputs where LiteRT declares int64, so the shim casts
  each tensor to the dtype its own graph declares.

`generate` addresses every tensor positionally, and the 24 cache tensors all
share one shape, so a graph whose IO happened to be ordered differently would
run to completion on the wrong layer's cache and show up only as a worse CER.
`_check_io_order` therefore refuses to build an engine whose graphs do not carry
exactly the names, in exactly the order, that `generate` assumes.

Graphs are not in the repo (~420 MB at fp16). Point `HAYAI_COREML_DIR` at a
directory holding `hayai_{encoder,prefill,decode}.mlpackage` plus the matching
`.io.json`, `position_base.npy` and `tokenizer.json`. `HAYAI_COREML_UNITS`
selects the compute units (`ALL` by default, or e.g. `CPU_ONLY`); the recorded
`artifact_sha256`, `graph_spec_version`, `graph_compression_ops` and
`graph_palette_bits` are what actually identify the measured graphs.
"""

from __future__ import annotations

import collections
import hashlib
import json
import math
import os
from collections.abc import Callable
from importlib.metadata import version
from pathlib import Path

import coremltools as ct
import numpy as np
from hayai_ocr.litert import TOTAL, LitertOcrEngine
from PIL import Image
from tokenizers import Tokenizer

SOURCE_MODEL = "JustANormalTinkerer/hayai-ocr-v2"
REVISION = "4cf1398f8d9a56a2d4bd7c1d2fb7648773660a62"  # checkpoint snapshot the graphs were converted from
GRAPHS = ("encoder", "prefill", "decode")
MODES = ("split", "merged")
MERGED_DECODER = "hayai_decoder.mlpackage"  # prefill+decode as two functions of one package
DECODE_POS_INPUT = 4  # index of `pos` in LitertOcrEngine.generate's decode input order
N_LAYERS = 12

# The IO `LitertOcrEngine.generate` addresses positionally, in its order.
ENCODER_INPUTS = ("pixel_values", "attention_mask", "pos_embeds")
DECODE_SCALAR_INPUTS = ("token_ids", "cos", "sin", "mask", "write_mask")
DECODE_CACHE_INPUTS = tuple(
    [f"past_k_{i}" for i in range(N_LAYERS)] + [f"past_v_{i}" for i in range(N_LAYERS)]
)
CACHE_OUTPUTS = tuple(name.replace("past_", "new_", 1) for name in DECODE_CACHE_INPUTS)

# coremltools ArrayFeatureType.ArrayDataType -> numpy. Total on purpose: an
# unmapped code means the graph declares something the shim has never cast to.
_ARRAY_DTYPE = {
    65568: np.float32,
    65552: np.float16,
    65600: np.float64,
    131104: np.int32,
    131080: np.int8,
}


def pos_to_write_mask(pos: int) -> np.ndarray:
    """The cache slot `pos` names, as the one-hot (1,1,320,1) the decode graph takes.

    `export_kv.DecodeDecoderKV` computes `arange(320) == pos` in-graph and selects
    with it; the converted graph takes the result instead.
    """
    pos = int(pos)
    if not 0 <= pos < TOTAL:
        raise ValueError(f"pos {pos} is outside the cache [0, {TOTAL}); no slot would be written")
    return (np.arange(TOTAL) == pos).reshape(1, 1, TOTAL, 1)


def _mil_operations(spec):
    """The operations of an mlprogram spec's `main` function."""
    function = spec.mlProgram.functions["main"]
    return function.block_specializations[function.opset].operations


def _mil_op_counts(spec) -> collections.Counter:
    """Operation-type histogram of an mlprogram spec's `main` function."""
    return collections.Counter(op.type for op in _mil_operations(spec))


def _lut_codes(op) -> int:
    """How many palette codes one `constexpr_lut_to_dense` op's LUT holds.

    The two opsets store the LUT differently. iOS16 carries it as an op attribute,
    rank 1, one element per code. iOS18 carries it as an immediate input shaped
    `[groups, ..., 2 ** nbits, vector_size]`, so there the code count is the
    second-to-last extent and the element count would also fold in the group and
    vector axes.
    """
    attribute = op.attributes.get("lut")
    if attribute is not None:
        return math.prod(d.constant.size for d in attribute.type.tensorType.dimensions)
    binding = op.inputs["lut"].arguments[0]
    extents = [d.constant.size for d in binding.value.type.tensorType.dimensions]
    return extents[-2] if len(extents) >= 2 else math.prod(extents)


def _palette_bits(spec) -> list[int]:
    """The distinct palette widths present, in bits.

    Palettised recipes carry the same `constexpr_lut_to_dense` histogram whatever
    their width, so the op counts alone read a 6-bit model and a 4-bit one alike.
    """
    widths = set()
    for op in _mil_operations(spec):
        if op.type.rsplit(".", 1)[-1] != "constexpr_lut_to_dense":
            continue
        codes = _lut_codes(op)
        if codes < 2 or codes & (codes - 1):
            raise ValueError(
                f"constexpr_lut_to_dense carries a {codes}-code LUT, which is not 2**nbits"
            )
        widths.add(codes.bit_length() - 1)
    return sorted(widths)


def _array_dtype(code: int, name: str) -> type:
    try:
        return _ARRAY_DTYPE[code]
    except KeyError:
        raise ValueError(
            f"input {name!r} declares ArrayDataType {code}, which the shim has no cast for "
            f"(known: {sorted(_ARRAY_DTYPE)})"
        ) from None


def _declared_inputs(spec, function_name: str | None):
    """The description's inputs, taken from the named function on a multifunction model.

    A multifunction package leaves `description.input` empty and carries each
    function's IO under `description.functions`, so reading the top level would find
    no inputs at all and every dtype lookup would raise on a name that is really there.
    """
    if function_name is None:
        return spec.description.input
    for function in spec.description.functions:
        if function.name == function_name:
            return function.input
    available = [function.name for function in spec.description.functions]
    raise ValueError(f"model declares no function {function_name!r}; it carries {available}")


def _check_functions(path: Path, expected: tuple[str, ...]) -> None:
    """Refuse a merged package that does not carry exactly the functions we address."""
    spec = ct.models.MLModel(str(path), skip_model_load=True).get_spec()
    names = tuple(function.name for function in spec.description.functions)
    if set(names) != set(expected):
        raise ValueError(f"{path.name} carries functions {names}, expected {expected}")


class _CoreMLGraph:
    """The slice of the LiteRT `Interpreter` surface that `generate` uses."""

    def __init__(self, path: Path, io: dict, compute_units: ct.ComputeUnit,
                 function_name: str | None = None):
        named = {} if function_name is None else {"function_name": function_name}
        self.model = ct.models.MLModel(str(path), compute_units=compute_units, **named)
        self._names = io["inputs"]
        self._outputs = io["outputs"]
        spec = self.model.get_spec()
        declared = {i.name: i.type.multiArrayType.dataType
                    for i in _declared_inputs(spec, function_name)}
        self._dtypes = [_array_dtype(declared[n], n) for n in self._names]
        self.spec_version = spec.specificationVersion
        self.op_counts = _mil_op_counts(spec)
        self.palette_bits = _palette_bits(spec)
        self._feed: dict[str, np.ndarray] = {}
        self._result: dict[str, np.ndarray] = {}

    @property
    def compression_ops(self) -> dict[str, int]:
        """The weight-compression ops actually present, which is what the run is."""
        # A compiled graph names ops by opset (`ios16.constexpr_lut_to_dense`);
        # an .mlpackage spec does not. Count either, so this cannot read 0 for
        # a compressed model and be mistaken for fp16.
        return {op: n for op, n in sorted(self.op_counts.items())
                if op.rsplit(".", 1)[-1].startswith("constexpr")}

    def get_input_details(self) -> list[dict]:
        return [{"index": i, "name": n} for i, n in enumerate(self._names)]

    def get_output_details(self) -> list[dict]:
        return [{"index": i, "name": n} for i, n in enumerate(self._outputs)]

    def set_tensor(self, index: int, value) -> None:
        self._feed[self._names[index]] = np.asarray(value).astype(self._dtypes[index], copy=False)

    def invoke(self) -> None:
        self._result = self.model.predict(self._feed)

    def get_tensor(self, index: int) -> np.ndarray:
        return self._result[self._outputs[index]]


class _DecodeGraph(_CoreMLGraph):
    """Decode step, whose `pos` scalar input becomes the graph's one-hot `write_mask`."""

    def set_tensor(self, index: int, value) -> None:
        if index == DECODE_POS_INPUT:
            value = pos_to_write_mask(np.asarray(value).item())
        super().set_tensor(index, value)


def _check_io_order(io: dict[str, dict]) -> None:
    """Refuse IO that `generate`'s positional addressing would silently misread."""
    enc_in, enc_out = tuple(io["encoder"]["inputs"]), tuple(io["encoder"]["outputs"])
    pre_out = tuple(io["prefill"]["outputs"])
    dec_in, dec_out = tuple(io["decode"]["inputs"]), tuple(io["decode"]["outputs"])
    expected_dec_in = DECODE_SCALAR_INPUTS + DECODE_CACHE_INPUTS
    expected_out = ("logits",) + CACHE_OUTPUTS

    if enc_in != ENCODER_INPUTS:
        raise ValueError(f"encoder inputs are {enc_in}, expected {ENCODER_INPUTS}")
    if len(enc_out) != 1:
        raise ValueError(f"encoder outputs are {enc_out}; generate reads exactly one")
    if dec_in != expected_dec_in:
        raise ValueError(f"decode inputs are {dec_in}, expected {expected_dec_in}")
    if pre_out != expected_out:
        raise ValueError(f"prefill outputs are {pre_out}, expected {expected_out}")
    if dec_out != expected_out:
        raise ValueError(f"decode outputs are {dec_out}, expected {expected_out}")
    if len(pre_out) - 1 != len(DECODE_CACHE_INPUTS):
        raise ValueError(
            f"prefill emits {len(pre_out) - 1} caches but decode takes {len(DECODE_CACHE_INPUTS)}"
        )


class CoreMLOcrEngine(LitertOcrEngine):
    """`LitertOcrEngine` with the three LiteRT interpreters replaced by CoreML models.

    `generate` is inherited unchanged; only `__init__` differs.
    """

    def __init__(self, model_dir: Path, compute_units: ct.ComputeUnit, mode: str = "split"):
        self.position_base = np.load(str(model_dir / "position_base.npy"))
        self.tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
        # `or` would swallow a legitimate id 0; LitertOcrEngine.__init__ tests for None
        self.bos_id = self.tokenizer.token_to_id("<bos>")
        self.eos_id = self.tokenizer.token_to_id("<eos>")
        self.pad_id = self.tokenizer.token_to_id("<pad>")
        if self.bos_id is None:
            self.bos_id = 1
        if self.eos_id is None:
            self.eos_id = 2
        if self.pad_id is None:
            self.pad_id = self.eos_id

        io = {g: json.loads((model_dir / f"hayai_{g}.io.json").read_text()) for g in GRAPHS}
        _check_io_order(io)

        self.enc = _CoreMLGraph(model_dir / "hayai_encoder.mlpackage", io["encoder"], compute_units)
        if mode == "merged":
            decoder = model_dir / MERGED_DECODER
            _check_functions(decoder, ("prefill", "decode"))
            self.prefill = _CoreMLGraph(decoder, io["prefill"], compute_units, "prefill")
            self.decode = _DecodeGraph(decoder, io["decode"], compute_units, "decode")
        else:
            self.prefill = _CoreMLGraph(model_dir / "hayai_prefill.mlpackage", io["prefill"], compute_units)
            self.decode = _DecodeGraph(model_dir / "hayai_decode.mlpackage", io["decode"], compute_units)

        self._enc_in = [d["index"] for d in self.enc.get_input_details()]
        self._enc_out = [d["index"] for d in self.enc.get_output_details()]
        self._pre_in = [d["index"] for d in self.prefill.get_input_details()]
        self._pre_out = [d["index"] for d in self.prefill.get_output_details()]
        self._dec_in = [d["index"] for d in self.decode.get_input_details()]
        self._dec_out = [d["index"] for d in self.decode.get_output_details()]

    def graphs(self) -> dict[str, _CoreMLGraph]:
        return {"encoder": self.enc, "prefill": self.prefill, "decode": self.decode}


def _digest(path: Path) -> str:
    """Content digest of an .mlpackage directory (path + bytes of every file, sorted)."""
    h = hashlib.sha256()
    for f in sorted(p for p in path.rglob("*") if p.is_file()):
        h.update(str(f.relative_to(path)).encode())
        with f.open("rb") as fh:
            for chunk in iter(lambda fh=fh: fh.read(1 << 20), b""):
                h.update(chunk)
    return h.hexdigest()[:16]


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    if device == "mps":
        raise RuntimeError("CoreML chooses its own compute units; run with --device cpu or auto "
                           "and set HAYAI_COREML_UNITS to pick them")

    raw = os.environ.get("HAYAI_COREML_DIR")
    if not raw:
        raise RuntimeError("set HAYAI_COREML_DIR to the directory holding the converted .mlpackage graphs")
    model_dir = Path(raw)
    mode = os.environ.get("HAYAI_COREML_MODE", "split")
    if mode not in MODES:
        raise RuntimeError(f"HAYAI_COREML_MODE must be one of {MODES}, got {mode!r}")
    packages = (["hayai_encoder.mlpackage", MERGED_DECODER] if mode == "merged"
                else [f"hayai_{g}.mlpackage" for g in GRAPHS])
    required = (packages + [f"hayai_{g}.io.json" for g in GRAPHS]
                + ["position_base.npy", "tokenizer.json"])
    missing = [name for name in required if not (model_dir / name).exists()]
    if missing:
        raise RuntimeError(f"HAYAI_COREML_DIR={raw} is missing {missing} for mode {mode}")

    units_name = os.environ.get("HAYAI_COREML_UNITS", "ALL")
    try:
        compute_units = ct.ComputeUnit[units_name]
    except KeyError:
        valid = [u.name for u in ct.ComputeUnit]
        raise RuntimeError(f"HAYAI_COREML_UNITS={units_name!r} is not a coremltools ComputeUnit; "
                           f"valid names are {valid}") from None
    engine = CoreMLOcrEngine(model_dir, compute_units, mode)
    engine.generate(Image.new("RGB", (64, 64), "white"))  # warm up outside the measured window

    graphs = engine.graphs()
    artifact_sha256 = {name: _digest(model_dir / name) for name in packages}
    spec_versions = {g: graphs[g].spec_version for g in GRAPHS}
    compression_ops = {g: graphs[g].compression_ops for g in GRAPHS}
    palette_bits = {g: graphs[g].palette_bits for g in GRAPHS}
    weights_label = os.environ.get("HAYAI_COREML_WEIGHTS", "")

    info = {
        "model": SOURCE_MODEL,
        "revision": REVISION,
        "device": f"mac-coreml-{units_name.lower().replace('_', '-')}",
        "artifact_sha256": artifact_sha256,
        "artifacts_resolved_via": f"HAYAI_COREML_DIR={raw}",
        "graph_spec_version": spec_versions,
        "graph_compression_ops": compression_ops,
        "graph_palette_bits": palette_bits,
        "coreml_mode": mode,
        "weights_label": weights_label,
        "settings": (
            f"coremltools {version('coremltools')} mlprogram, converted at compute_precision=FLOAT16, "
            f"compute_units={units_name}, mode={mode} ("
            + ("encoder + one package carrying prefill and decode as two functions, so the decoder "
               "weights they share are stored once" if mode == "merged"
               else "three separate packages") + "); the graphs' own specificationVersion is {spec_versions} "
            "(7 = iOS16), their weight-compression op counts are "
            f"{compression_ops} and their palette widths in bits are {palette_bits} "
            "(empty where no LUT is present; palettised recipes share one op histogram and "
            f"differ only here) -- those three fields, not the operator-supplied "
            f"weights_label={weights_label!r}, are what identify the weights. Compression is applied by "
            "coremltools.optimize.coreml at its default weight_threshold=2048, so tensors with fewer "
            "than 2048 elements (layer-norm weights, the per-layer residual scales, biases) are left "
            "fp16 and are not covered by the label; "
            f"graphs converted from torch checkpoint {SOURCE_MODEL}@{REVISION[:7]} via torch.export, "
            "fixed shapes MAX_PATCHES=256 / MAX_TEXT=64 (encoder + decoder prefill + decoder decode); "
            f"host side is hayai-ocr {version('hayai-ocr')} LitertOcrEngine.generate inherited verbatim -- "
            "SigLIP2 NaFlex preprocessing, position-embedding resize, 2D mRoPE and block-causal masks in "
            "numpy; greedy argmax, repetition_penalty=1.0, max_new_tokens=64 (graph limit, same as the "
            "LiteRT run, vs 128 on the torch run); output = tokenizer decode with skip_special_tokens "
            "(no extra post-processing)"
        ),
    }
    return engine.generate, info
