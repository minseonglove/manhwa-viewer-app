import argparse
import json
import sys

from PIL import Image, ImageDraw

from ocr_eval.detectors import run_koharu, run_ogkalu
from ocr_eval.overlay import draw_grid
from ocr_eval.paths import PAGES_DIR, PROPOSALS_DIR, REVIEW_DIR
from ocr_eval.proposals import BUBBLE_LABELS, TEXT_LABELS, merge_text_proposals


def propose(page: str) -> dict:
    image = Image.open(PAGES_DIR / f"{page}.jpg").convert("RGB")
    raw = run_koharu(image) + run_ogkalu(image)
    text = merge_text_proposals([p for p in raw if (p.source, p.label) in TEXT_LABELS])
    bubbles = [
        {"source": p.source, "score": round(p.score, 3), "bbox": list(p.bbox)}
        for p in raw if (p.source, p.label) in BUBBLE_LABELS
    ]
    data = {"page": page, "width": image.width, "height": image.height, "text": text, "bubbles": bubbles}

    overlay = draw_grid(image)
    draw = ImageDraw.Draw(overlay)
    for bubble in bubbles:
        x, y, w, h = bubble["bbox"]
        draw.rectangle([x, y, x + w, y + h], outline=(0, 160, 0), width=1)
    for entry in text:
        x, y, w, h = entry["bbox"]
        draw.rectangle([x, y, x + w, y + h], outline=(230, 0, 0), width=2)
        tag_top = max(0, y - 16)
        draw.rectangle([x, tag_top, x + 22, tag_top + 16], fill=(230, 0, 0))
        draw.text((x + 2, tag_top + 1), str(entry["n"]), fill="white")
    (REVIEW_DIR / "overlay").mkdir(parents=True, exist_ok=True)
    overlay.save(REVIEW_DIR / "overlay" / f"{page}.png")
    return data


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("pages", nargs="*", help="page names; default: all pages in data/pages")
    args = parser.parse_args()
    pages = args.pages or sorted(p.stem for p in PAGES_DIR.glob("*.jpg"))
    PROPOSALS_DIR.mkdir(parents=True, exist_ok=True)
    for page in pages:
        data = propose(page)
        (PROPOSALS_DIR / f"{page}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"{page}: text={len(data['text'])} bubbles={len(data['bubbles'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
