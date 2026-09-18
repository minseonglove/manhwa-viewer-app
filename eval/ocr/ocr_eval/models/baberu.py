"""Baberu OCR adapter (genshiai-daichi/baberu-ocr).

Downloads the model's own `onnx_infer.py` and the lossless ONNX tier
(`vision_fp16.onnx` + `decoder_*_int8.onnx`) at a pinned revision, then loads
and reuses that script's `BaberuOnnxOCR` class directly so recognition,
preprocessing and the KV-cache decode loop stay byte-for-byte identical to the
official inference path. Only onnxruntime's CPUExecutionProvider is used, per
the model card's own example; there is no CoreML EP in the published script.
"""

from __future__ import annotations

import importlib.util
from collections.abc import Callable
from pathlib import Path
from types import ModuleType

from huggingface_hub import snapshot_download
from PIL import Image

REPO_ID = "genshiai-daichi/baberu-ocr"
REVISION = "d9cc13153e9a1cd8fdfa3b7b1cc329da2020aeae"  # pinned via HF API `sha`
VISION_FILE = "vision_fp16.onnx"  # lossless tier per the model card

ALLOW_PATTERNS = [
    "onnx_infer.py",
    f"onnx/{VISION_FILE}",
    "onnx/decoder_prefill_int8.onnx",
    "onnx/decoder_step_int8.onnx",
    "tokenizer/vocab.json",
]


def _load_onnx_infer(repo_dir: Path) -> ModuleType:
    spec = importlib.util.spec_from_file_location("baberu_onnx_infer", repo_dir / "onnx_infer.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    if device == "mps":
        raise ValueError("baberu is CPU-only (the official onnx_infer.py has no MPS/CoreML path); use auto or cpu")
    repo_dir = Path(snapshot_download(repo_id=REPO_ID, revision=REVISION, allow_patterns=ALLOW_PATTERNS))
    onnx_infer = _load_onnx_infer(repo_dir)
    ocr = onnx_infer.BaberuOnnxOCR(repo_dir / "onnx", repo_dir / "tokenizer", vision=VISION_FILE)
    ocr(Image.new("RGB", (224, 224), "white"))  # warm up graph execution before timing starts

    info = {
        "model": REPO_ID,
        "revision": REVISION,
        "device": "mac-cpu",
        "settings": (
            "greedy, repetition_penalty=1.2, max_content_run=12 (symbol-aware content-run cap), "
            f"vision={VISION_FILE} + decoder_*_int8 (lossless tier, onnxruntime CPUExecutionProvider)"
        ),
    }
    return ocr, info
