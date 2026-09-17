from __future__ import annotations

import argparse
import re
import sys
import time
from datetime import datetime
from importlib.metadata import version
from pathlib import Path
from collections.abc import Callable

from PIL import Image

from ocr_eval.crop import crop_item
from ocr_eval.fingerprint import gt_fingerprint
from ocr_eval.gt import GtPage, load_pages, ready_pages
from ocr_eval.paths import GT_DIR, PAGES_DIR
from ocr_eval.runs import git_commit, new_run_dir, write_json


def recognize_pages(
    pages: list[GtPage],
    pages_dir: Path,
    ocr: Callable[[Image.Image], str],
) -> dict[str, dict]:
    results = {}
    for page in pages:
        image = Image.open(pages_dir / f"{page.page}.jpg").convert("RGB")
        for item in page.items:
            crop = crop_item(image, item.bbox)
            start = time.perf_counter()
            text = ocr(crop)
            results[f"{page.page}/{item.id}"] = {"text": text, "ms": round((time.perf_counter() - start) * 1000)}
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="jzhang533/manga-ocr-base-2025")
    parser.add_argument("--device", choices=["cpu", "mps"], default="cpu")
    args = parser.parse_args()

    from manga_ocr import MangaOcr

    pages = ready_pages(load_pages(GT_DIR))
    if not pages:
        raise SystemExit("no ready ground truth pages")
    # 공식 manga-ocr 경로를 그대로 쓴다: 커스텀 전후처리를 넣으면 기준값 자체가 재현 불가능해진다
    ocr = MangaOcr(pretrained_model_name_or_path=args.model, force_cpu=args.device == "cpu")
    results = recognize_pages(pages, PAGES_DIR, ocr)
    expected = sum(len(page.items) for page in pages)
    if len(results) != expected:
        raise SystemExit(f"processed {len(results)} items, expected {expected}")

    slug = re.sub(r"[^a-z0-9]+", "-", args.model.lower()).strip("-")
    run_dir = new_run_dir(f"mac-reference-{slug}")
    write_json(run_dir / "meta.json", {
        "runner": "mac-reference",
        "model": args.model,
        "commit": git_commit(),
        "device": f"mac-{ocr.model.device.type}",
        "manga_ocr": version("manga-ocr"),
        "transformers": version("transformers"),
        "torch": version("torch"),
        "created_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "gt_fingerprint": gt_fingerprint(pages, PAGES_DIR),
    })
    write_json(run_dir / "recognition.json", results)
    print(run_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
