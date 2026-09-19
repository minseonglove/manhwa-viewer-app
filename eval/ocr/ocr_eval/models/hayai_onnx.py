"""Hayai OCR v2.1 ONNX adapter (graphs exported from the torch checkpoint).

Measures what an Android build would run if Hayai went through the runtime the app already
carries. The app ships ONNX Runtime and no LiteRT, so "Android = LiteRT" -- true of what the
model's author publishes -- is not automatically true of this app; this adapter exists to price
the alternative.

The graphs are the *same static rewrites* the CoreML conversion uses (`hayai_static.py`):
real-valued RoPE, every extent a Python constant, and decode's `pos` scalar replaced by a
host-computed one-hot `write_mask`. None of those are ONNX limitations -- reusing one set of
modules means a score difference between backends is the runtime, not the rewrite.

Everything outside graph execution stays on the author's official path: this module subclasses
`hayai_ocr.litert.LitertOcrEngine` and inherits `generate` verbatim, so preprocessing, the
position-embedding resize, 2D mRoPE, the masks, the greedy loop and the tokenizer decode are the
same code every other measured run used. Only the objects `generate` calls
`set_tensor`/`invoke`/`get_tensor` on are swapped.

Graphs are not in the repo (~845 MB at fp32, ~214 MB at int8). Point `HAYAI_ONNX_DIR` at a
directory holding `hayai_{encoder,prefill,decode}.onnx` plus the matching `.io.json`,
`position_base.npy` and `tokenizer.json`. `HAYAI_ONNX_PROVIDERS` selects execution providers as
a comma-separated list (default `CPUExecutionProvider`). The recorded `artifact_sha256` and
`graph_weight_dtypes` are what identify the measured graphs, not the directory name.
"""

from __future__ import annotations

import hashlib
import json
import os
from collections import Counter
from collections.abc import Callable
from importlib.metadata import version
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from hayai_ocr.litert import TOTAL, LitertOcrEngine
from PIL import Image
from tokenizers import Tokenizer

from ._hayai_graph_io import (
    DECODE_POS_INPUT, GRAPHS, REVISION, SOURCE_MODEL,
    check_io_order, pos_to_write_mask,
)

_ORT_DTYPE = {
    "tensor(float)": np.float32,
    "tensor(float16)": np.float16,
    "tensor(double)": np.float64,
    "tensor(int64)": np.int64,
    "tensor(int32)": np.int32,
    "tensor(bool)": np.bool_,
}


def _ort_dtype(declared: str, name: str) -> type:
    try:
        return _ORT_DTYPE[declared]
    except KeyError:
        raise ValueError(
            f"input {name!r} declares {declared}, which the shim has no cast for "
            f"(known: {sorted(_ORT_DTYPE)})"
        ) from None


class _OnnxGraph:
    """The slice of the LiteRT `Interpreter` surface that `generate` uses."""

    def __init__(self, path: Path, io: dict, providers: list[str]):
        self.session = ort.InferenceSession(str(path), providers=providers)
        declared = {i.name: i.type for i in self.session.get_inputs()}
        self._names = io["inputs"]
        self._outputs = io["outputs"]
        self._dtypes = [_ort_dtype(declared[n], n) for n in self._names]
        self._feed: dict[str, np.ndarray] = {}
        self._result: dict[str, np.ndarray] = {}

    def get_input_details(self) -> list[dict]:
        return [{"index": i, "name": n} for i, n in enumerate(self._names)]

    def get_output_details(self) -> list[dict]:
        return [{"index": i, "name": n} for i, n in enumerate(self._outputs)]

    def set_tensor(self, index: int, value) -> None:
        self._feed[self._names[index]] = np.asarray(value).astype(self._dtypes[index], copy=False)

    def invoke(self) -> None:
        outputs = self.session.run(self._outputs, self._feed)
        self._result = dict(zip(self._outputs, outputs))

    def get_tensor(self, index: int) -> np.ndarray:
        return self._result[self._outputs[index]]


class _DecodeGraph(_OnnxGraph):
    """Decode step, whose `pos` scalar input becomes the graph's one-hot `write_mask`."""

    def set_tensor(self, index: int, value) -> None:
        if index == DECODE_POS_INPUT:
            value = pos_to_write_mask(np.asarray(value).item(), TOTAL)
        super().set_tensor(index, value)


class OnnxOcrEngine(LitertOcrEngine):
    """`LitertOcrEngine` with the three LiteRT interpreters replaced by ONNX Runtime sessions.

    `generate` is inherited unchanged; only `__init__` differs.
    """

    def __init__(self, model_dir: Path, providers: list[str]):
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
        check_io_order(io)

        self.enc = _OnnxGraph(model_dir / "hayai_encoder.onnx", io["encoder"], providers)
        self.prefill = _OnnxGraph(model_dir / "hayai_prefill.onnx", io["prefill"], providers)
        self.decode = _DecodeGraph(model_dir / "hayai_decode.onnx", io["decode"], providers)

        self._enc_in = [d["index"] for d in self.enc.get_input_details()]
        self._enc_out = [d["index"] for d in self.enc.get_output_details()]
        self._pre_in = [d["index"] for d in self.prefill.get_input_details()]
        self._pre_out = [d["index"] for d in self.prefill.get_output_details()]
        self._dec_in = [d["index"] for d in self.decode.get_input_details()]
        self._dec_out = [d["index"] for d in self.decode.get_output_details()]

    def graphs(self) -> dict[str, _OnnxGraph]:
        return {"encoder": self.enc, "prefill": self.prefill, "decode": self.decode}


def _digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()[:16]


def _weight_dtypes(path: Path) -> dict[str, int]:
    """Initializer element types actually present, which is what the run is.

    Read from the file rather than taken from the directory name: an int8 graph and a float one
    are told apart here, not by a label an operator typed.
    """
    model = onnx.load(str(path), load_external_data=False)
    counts = Counter(
        onnx.TensorProto.DataType.Name(init.data_type) for init in model.graph.initializer
    )
    return dict(sorted(counts.items()))


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    if device == "mps":
        raise RuntimeError("ONNX Runtime chooses its own execution provider; run with "
                           "--device cpu or auto and set HAYAI_ONNX_PROVIDERS to pick one")

    raw = os.environ.get("HAYAI_ONNX_DIR")
    if not raw:
        raise RuntimeError("set HAYAI_ONNX_DIR to the directory holding the exported .onnx graphs")
    model_dir = Path(raw)
    required = ([f"hayai_{g}.onnx" for g in GRAPHS] + [f"hayai_{g}.io.json" for g in GRAPHS]
                + ["position_base.npy", "tokenizer.json"])
    missing = [name for name in required if not (model_dir / name).exists()]
    if missing:
        raise RuntimeError(f"HAYAI_ONNX_DIR={raw} is missing {missing}")

    requested = os.environ.get("HAYAI_ONNX_PROVIDERS", "CPUExecutionProvider")
    providers = [p.strip() for p in requested.split(",") if p.strip()]
    available = ort.get_available_providers()
    unknown = [p for p in providers if p not in available]
    if unknown:
        raise RuntimeError(f"HAYAI_ONNX_PROVIDERS={requested!r} names {unknown}, which this "
                           f"onnxruntime build does not have; available are {available}")

    engine = OnnxOcrEngine(model_dir, providers)
    engine.generate(Image.new("RGB", (64, 64), "white"))  # warm up outside the measured window

    artifact_sha256 = {f"hayai_{g}.onnx": _digest(model_dir / f"hayai_{g}.onnx") for g in GRAPHS}
    weight_dtypes = {g: _weight_dtypes(model_dir / f"hayai_{g}.onnx") for g in GRAPHS}
    used = {g: engine.graphs()[g].session.get_providers() for g in GRAPHS}

    info = {
        "model": SOURCE_MODEL,
        "revision": REVISION,
        "device": f"mac-onnx-{'-'.join(p.replace('ExecutionProvider', '').lower() for p in providers)}",
        "artifact_sha256": artifact_sha256,
        "artifacts_resolved_via": f"HAYAI_ONNX_DIR={raw}",
        "graph_weight_dtypes": weight_dtypes,
        "providers_used": used,
        "settings": (
            f"onnxruntime {version('onnxruntime')}, providers={providers} (actually used: {used}); "
            f"initializer element types per graph are {weight_dtypes} -- that field, not the "
            "directory name, is what identifies the weights; graphs exported from torch "
            f"checkpoint {SOURCE_MODEL}@{REVISION[:7]} via torch.onnx.export(dynamo=True) at "
            "opset 18 from the same static modules the CoreML conversion uses (real-valued RoPE, "
            "constant extents, decode's pos replaced by a host-computed write_mask), fixed shapes "
            "MAX_PATCHES=256 / MAX_TEXT=64; host side is hayai-ocr "
            f"{version('hayai-ocr')} LitertOcrEngine.generate inherited verbatim -- SigLIP2 NaFlex "
            "preprocessing, position-embedding resize, 2D mRoPE and block-causal masks in numpy; "
            "greedy argmax, repetition_penalty=1.0, max_new_tokens=64 (graph limit, same as the "
            "LiteRT and CoreML runs, vs 128 on the torch run); output = tokenizer decode with "
            "skip_special_tokens (no extra post-processing)"
        ),
    }
    return engine.generate, info
