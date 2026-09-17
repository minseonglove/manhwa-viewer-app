from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from ocr_eval.gt import GtPage, load_pages, ready_pages
from ocr_eval.paths import GT_DIR, PAGES_DIR


def gt_fingerprint(pages: list[GtPage], pages_dir: Path) -> str:
    digest = hashlib.sha256()
    for page in sorted(pages, key=lambda p: p.page):
        digest.update(page.page.encode("utf-8"))
        digest.update((pages_dir / f"{page.page}.jpg").read_bytes())
        for item in sorted(page.items, key=lambda i: i.id):
            x, y, w, h = item.bbox
            digest.update(f"{item.id}:{x},{y},{w},{h};".encode("utf-8"))
    return digest.hexdigest()[:16]


def main() -> int:
    print(gt_fingerprint(ready_pages(load_pages(GT_DIR)), PAGES_DIR))
    return 0


if __name__ == "__main__":
    sys.exit(main())
