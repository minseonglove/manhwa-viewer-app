import argparse
import json
import sys

from ocr_eval.gt import GtItem, GtPage, page_to_dict
from ocr_eval.paths import GT_DIR, PROPOSALS_DIR
from ocr_eval.score_pipeline import overlap_ratio

INSIDE_BUBBLE = 0.7


def _category(entry: dict, bubbles: list[dict]) -> str:
    if entry["label"] == "onomatopoeia":
        return "sfx"
    if entry["label"] == "text_bubble":
        return "bubble"
    if entry["label"] == "text_free":
        return "aside"
    inside = any(overlap_ratio(tuple(entry["bbox"]), tuple(b["bbox"])) >= INSIDE_BUBBLE for b in bubbles)
    return "bubble" if inside else "aside"


def draft_items(proposals: dict) -> list[GtItem]:
    items = []
    for index, entry in enumerate(proposals["text"], start=1):
        x, y, w, h = entry["bbox"]
        sources = ",".join(sorted({s["source"] for s in entry["sources"]}))
        items.append(GtItem(
            id=f"t{index:02d}",
            bbox=(x, y, w, h),
            category=_category(entry, proposals["bubbles"]),
            orientation="horizontal" if w > h else "vertical",
            text="",
            note=f"n={entry['n']} label={entry['label']} sources={sources}",
        ))
    return items


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--page", required=True)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    output = GT_DIR / f"{args.page}.json"
    if output.exists() and not args.force:
        raise SystemExit(f"{output} exists; use --force to overwrite")
    proposals = json.loads((PROPOSALS_DIR / f"{args.page}.json").read_text(encoding="utf-8"))
    page = GtPage(page=args.page, width=proposals["width"], height=proposals["height"],
                  items=tuple(draft_items(proposals)), draft=True)
    GT_DIR.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(page_to_dict(page), ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{output}: {len(page.items)} draft items")
    return 0


if __name__ == "__main__":
    sys.exit(main())
