"""Hayai OCR v2.1 LiteRT/TFLite adapter (JustANormalTinkerer/hayai-ocr-v2-tflite).

Measures the *mobile* artifacts the model author publishes, rather than the torch
checkpoint measured by `ocr_eval.models.hayai`. Recognition goes through the same
library entry point (`HayaiOcr`) with `backend="litert"`, so preprocessing
(SigLIP2 NaFlex scaling to <=256 patches), 2D mRoPE, greedy decoding and
detokenization stay on the author's official path -- only the executed graphs
differ (`hayai_ocr/litert.py`).

Two things differ from the torch run by construction, not by choice:

* `max_new_tokens` is capped at 64. The exported decode graph has a fixed
  `MAX_TEXT = 64` text slot (`hayai_ocr/litert.py:28`), and `HayaiOcr`
  clamps to it, so 128 is not expressible.
* Weights are weight-only quantized (int4 / int8) by `ai_edge_quantizer`;
  activations stay fp32.

Which quant is measured comes from `HAYAI_LITERT_QUANT` (default `wi4`), since
`ocr_eval.run_model` has no per-adapter CLI flag.

Artifacts are pinned to `REVISION`. `snapshot_download` is used normally; set
`HAYAI_LITERT_DIR` to a directory that already holds `litert_exports/<quant>/`
materialized at that same revision to run without network access (the recorded
`artifact_sha256` is what actually identifies the measured graphs).
"""

from __future__ import annotations

import hashlib
import os
from collections.abc import Callable
from importlib.metadata import version
from pathlib import Path

from hayai_ocr import HayaiOcr
from hayai_ocr.litert import QUANT_SUFFIX, normalize_litert_quant
from PIL import Image

REPO_ID = "JustANormalTinkerer/hayai-ocr-v2-tflite"
REVISION = "f73d1c1b0c9cb53e42aaa8ec8b3625ca3ffc5d21"  # pinned via HF API `sha`
SOURCE_MODEL = "JustANormalTinkerer/hayai-ocr-v2"  # torch checkpoint these graphs were exported from
DEFAULT_QUANT = "wi4"


def _resolve_dir(quant: str) -> tuple[Path, str]:
    """Return (directory holding the quant's .tflite files, how it was resolved)."""
    override = os.environ.get("HAYAI_LITERT_DIR")
    if override:
        root = Path(override)
        candidate = root / "litert_exports" / quant
        if not candidate.is_dir():
            candidate = root / quant if (root / quant).is_dir() else root
        if not list(candidate.glob("*.tflite")):
            raise RuntimeError(f"HAYAI_LITERT_DIR={override} has no .tflite files for quant {quant} (looked in {candidate})")
        return candidate, f"HAYAI_LITERT_DIR at revision {REVISION}"

    from huggingface_hub import snapshot_download

    snapshot = Path(snapshot_download(repo_id=REPO_ID, revision=REVISION, allow_patterns=[f"litert_exports/{quant}/*"]))
    return snapshot / "litert_exports" / quant, "huggingface_hub snapshot_download"


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    if device == "mps":
        raise RuntimeError("the LiteRT backend has no Metal/MPS path on macOS; run with --device cpu or auto")

    quant = normalize_litert_quant(os.environ.get("HAYAI_LITERT_QUANT", DEFAULT_QUANT))
    quant_dir, resolved_via = _resolve_dir(quant)

    suffix = QUANT_SUFFIX[quant]
    artifact_sha256 = {}
    for kind in ("encoder", "decoder_prefill", "decoder_decode"):
        path = quant_dir / f"hayai_{kind}_{suffix}.tflite"
        artifact_sha256[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()[:16]

    ocr = HayaiOcr(backend="litert", litert_quant=quant, litert_model_path=str(quant_dir))
    # Both torch and litert backends warm up in their constructors. This extra call
    # is defensive/redundant but ensures no lazy-init cost leaks to the first measured item.
    ocr(Image.new("RGB", (64, 64), "white"))

    info = {
        "model": REPO_ID,
        "revision": REVISION,
        "source_model": SOURCE_MODEL,
        "device": "mac-cpu-litert",
        "quant": quant,
        "artifact_sha256": artifact_sha256,
        "artifacts_resolved_via": resolved_via,
        "settings": (
            f"hayai-ocr {version('hayai-ocr')} HayaiOcr backend='litert' quant={quant} "
            f"via ai-edge-litert {version('ai-edge-litert')} (CPU, default XNNPACK, default threads); "
            "author's exported KV-cache graphs (encoder + decoder prefill + decoder decode) with "
            "fixed shapes MAX_PATCHES=256 / MAX_TEXT=64; SigLIP2 NaFlex preprocessing, 2D mRoPE and "
            "block-causal masks computed host-side in numpy; greedy argmax, repetition_penalty=1.0, "
            "max_new_tokens=64 (graph limit, vs 128 on the torch run), weight-only quantized weights "
            "with fp32 activations, output = tokenizer decode with skip_special_tokens (no extra post-processing)"
        ),
    }
    return ocr, info
