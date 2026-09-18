"""PaddleOCR-VL-For-Manga adapter (jzhang533/PaddleOCR-VL-For-Manga).

Follows the fine-tune author's transformers inference path: the demo Space
(`jzhang533/paddleocr-vl-for-manga-demo`, `app.py::perform_ocr`) and the GitHub
repo's crop evaluation (`evaluation/eval_checkpoint.py::generate_predictions`),
which agree on everything used here: `trust_remote_code=True`, bfloat16, the
`OCR:` task prompt through the repo's chat template, greedy
`generate(do_sample=False, use_cache=True)`, and `batch_decode(skip_special_tokens=True)`
over the generated tokens only. Model and processor both come from the model repo
at one pinned revision, as in the Space. The remote code targets transformers
4.57.1, so this adapter runs from `.venv-paddleocr_vl_manga`.
"""

from __future__ import annotations

import importlib.metadata
from collections.abc import Callable

import torch
from PIL import Image
from transformers import AutoModelForCausalLM, AutoProcessor

REPO_ID = "jzhang533/PaddleOCR-VL-For-Manga"
REVISION = "1e8aa5f1dd90cc86fe9137c9c0b26ebde613cfe8"  # pinned via HF API `sha`
PROMPT = "OCR:"
# eval_checkpoint.py's default and the base PaddleOCR-VL card's transformers example
MAX_NEW_TOKENS = 1024
DTYPE = torch.bfloat16
REQUIRED_TRANSFORMERS_VERSION = "4.57.1"  # the remote code targets this; other versions load silently but decode garbage


def _resolve_device(device: str) -> str:
    if device == "auto":
        return "mps" if torch.backends.mps.is_available() else "cpu"
    return device


def load(device: str) -> tuple[Callable[[Image.Image], str], dict]:
    installed_transformers = importlib.metadata.version("transformers")
    if installed_transformers != REQUIRED_TRANSFORMERS_VERSION:
        raise RuntimeError(
            "paddleocr_vl_manga requires transformers=="
            f"{REQUIRED_TRANSFORMERS_VERSION} (found {installed_transformers}). Run it from the "
            ".venv-paddleocr_vl_manga venv (pip install -r requirements-paddleocr_vl_manga.txt), "
            "not the main .venv."
        )
    torch_device = _resolve_device(device)
    model = AutoModelForCausalLM.from_pretrained(
        REPO_ID, revision=REVISION, trust_remote_code=True, dtype=DTYPE
    ).to(torch_device).eval()
    processor = AutoProcessor.from_pretrained(REPO_ID, revision=REVISION, trust_remote_code=True, use_fast=True)

    def ocr(image: Image.Image) -> str:
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": PROMPT},
                ],
            }
        ]
        text = processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        inputs = processor(text=[text], images=[image], return_tensors="pt").to(torch_device)
        with torch.inference_mode():
            generated = model.generate(**inputs, max_new_tokens=MAX_NEW_TOKENS, do_sample=False, use_cache=True)
        return processor.batch_decode(generated[:, inputs["input_ids"].shape[1]:], skip_special_tokens=True)[0]

    ocr(Image.new("RGB", (224, 224), "white"))  # warm up kernels before timing starts

    info = {
        "model": REPO_ID,
        "revision": REVISION,
        "device": f"mac-{torch_device}",
        "settings": (
            f"greedy (do_sample=False, use_cache=True), max_new_tokens={MAX_NEW_TOKENS}, prompt '{PROMPT}' via repo chat "
            f"template, bfloat16, {model.config._attn_implementation} attention, processor from model repo (use_fast=True), "
            f"batch_decode(skip_special_tokens=True), transformers {installed_transformers} remote code"
        ),
    }
    return ocr, info
