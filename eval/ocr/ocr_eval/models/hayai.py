"""Hayai OCR v2.1 adapter (JustANormalTinkerer/hayai-ocr-v2).

Recognition goes through the author's `hayai-ocr` library (`HayaiOcr`), which
the model card recommends for Python apps, so the SigLIP2 NaFlex preprocessing
(fixed `max_num_patches=256`) and greedy decoding (`max_new_tokens=128`,
`repetition_penalty=1.0`) stay identical to the official path. The model repo
is handed to the library as a local snapshot at a pinned revision. The library
(`hayai_ocr/ocr.py`) and the model's own `modeling_hayai.py` both load
`google/siglip2-base-patch16-naflex` without a revision argument, so `load`
refuses to run unless that repo's current head is the pinned commit.
"""

from __future__ import annotations

from collections.abc import Callable
from importlib.metadata import version

import torch
from hayai_ocr import HayaiOcr
from huggingface_hub import HfApi, snapshot_download
from PIL import Image

REPO_ID = "JustANormalTinkerer/hayai-ocr-v2"
REVISION = "4cf1398f8d9a56a2d4bd7c1d2fb7648773660a62"  # pinned via HF API `sha`
PROCESSOR_REPO_ID = "google/siglip2-base-patch16-naflex"
PROCESSOR_REVISION = "b53b807d3a2d5e2b3911292f2d69e5341cdc064c"  # pinned via HF API `sha`


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    processor_head = HfApi().model_info(PROCESSOR_REPO_ID).sha
    if processor_head != PROCESSOR_REVISION:
        raise RuntimeError(
            f"{PROCESSOR_REPO_ID} head moved to {processor_head}; the pinned revision is {PROCESSOR_REVISION}"
        )
    model_dir = snapshot_download(repo_id=REPO_ID, revision=REVISION)
    if device == "auto":
        device = "mps" if torch.backends.mps.is_available() else "cpu"
    # The constructor also warms up on the library's bundled example image.
    ocr = HayaiOcr(pretrained_model_name_or_path=model_dir, device=device)

    info = {
        "model": REPO_ID,
        "revision": REVISION,
        "processor": PROCESSOR_REPO_ID,
        "processor_revision": PROCESSOR_REVISION,
        "device": f"mac-{ocr.device}",
        "settings": (
            f"hayai-ocr {version('hayai-ocr')} HayaiOcr torch backend with library defaults: "
            "SigLIP2 NaFlex processor max_num_patches=256 (fixed), greedy (num_beams=1), "
            "max_new_tokens=128, repetition_penalty=1.0, float32, no quantization, "
            "output = tokenizer decode with skip_special_tokens (no extra post-processing)"
        ),
    }
    return ocr, info
