import argparse
import sys
from pathlib import Path

from PIL import Image

from ocr_eval.crop import crop_item
from ocr_eval.fingerprint import gt_fingerprint
from ocr_eval.gt import load_pages, ready_pages, validate_page
from ocr_eval.paths import GT_DIR, PAGES_DIR
from ocr_eval.report import count_gaps, render_compare, render_report
from ocr_eval.runs import load_run


def _load_valid_pages():
    pages = load_pages(GT_DIR)
    ready = ready_pages(pages)
    invalid = [page.page for page in ready if validate_page(page, Image.open(PAGES_DIR / f"{page.page}.jpg").size)]
    if invalid:
        raise SystemExit(f"invalid ground truth: {invalid} — run python -m ocr_eval.validate_gt")
    if not ready:
        raise SystemExit("no ready ground truth pages")
    return ready, len(pages) - len(ready)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("run", type=Path)
    parser.add_argument("--compare", type=Path, help="baseline run; writes <run>/compare-<baseline>.md")
    args = parser.parse_args()

    pages, skipped = _load_valid_pages()
    run = load_run(args.run)

    if args.compare:
        baseline = load_run(args.compare)
        baseline_fingerprint = baseline.meta.get("gt_fingerprint")
        run_fingerprint = run.meta.get("gt_fingerprint")
        if baseline_fingerprint is None or run_fingerprint is None or baseline_fingerprint != run_fingerprint:
            print(f"gt_fingerprint mismatch: {args.compare}={baseline_fingerprint} {args.run}={run_fingerprint}",
                  file=sys.stderr)
            return 2
        markdown = render_compare(baseline, run, pages, skipped)
        output = run.path / f"compare-{args.compare.name}.md"
        output.write_text(markdown, encoding="utf-8")
        print(markdown.splitlines()[0])
        print(output)
        return 0

    current_fingerprint = gt_fingerprint(pages, PAGES_DIR)
    gt_status = "match" if run.meta.get("gt_fingerprint") == current_fingerprint else "STALE"
    markdown, worst = render_report(run, pages, skipped, gt_status=gt_status)
    output = run.path / "report.md"
    crops_dir = run.path / "crops"
    crops_dir.mkdir(exist_ok=True)
    images = {}
    for record in worst:
        image = images.setdefault(record.page, Image.open(PAGES_DIR / f"{record.page}.jpg").convert("RGB"))
        crop_item(image, record.item.bbox).save(crops_dir / f"{record.page}_{record.item.id}.png")

    output.write_text(markdown, encoding="utf-8")
    print(markdown.splitlines()[0])
    print(output)

    gaps = count_gaps(run, pages)
    if gaps["missing_recognition"] or gaps["unknown"] or gaps["missing_pipeline"] or gt_status == "STALE":
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
