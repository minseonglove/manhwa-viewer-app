import sys
from pathlib import Path

from PIL import Image

from ocr_eval.gt import GtPage, load_page, ready_pages, validate_page
from ocr_eval.paths import GT_DIR, PAGES_DIR


def check_filenames(paths: list[Path], pages: list[GtPage]) -> list[str]:
    errors = []
    files_by_page: dict[str, list[str]] = {}
    for path, page in zip(paths, pages):
        if path.stem != page.page:
            errors.append(f"{path.name}: file stem {path.stem!r} != page field {page.page!r}")
        files_by_page.setdefault(page.page, []).append(path.name)
    for page_name, files in files_by_page.items():
        if len(files) > 1:
            errors.append(f"{page_name}: declared by multiple files: {', '.join(files)}")
    return errors


def main() -> int:
    paths = sorted(GT_DIR.glob("*.json"))
    pages = [load_page(path) for path in paths]
    failed = 0
    for error in check_filenames(paths, pages):
        print(error)
        failed += 1

    ready = ready_pages(pages)
    for page in ready:
        image_path = PAGES_DIR / f"{page.page}.jpg"
        image_size = Image.open(image_path).size if image_path.exists() else None
        errors = validate_page(page, image_size)
        if image_size is None:
            errors.insert(0, f"missing page image {image_path.name}")
        for error in errors:
            print(f"{page.page}: {error}")
        failed += bool(errors)
    items = sum(len(page.items) for page in ready)
    unreadable = sum(item.unreadable for page in ready for item in page.items)
    uncertain = sum(item.uncertain for page in ready for item in page.items)
    print(f"ready pages={len(ready)} items={items} unreadable={unreadable} uncertain={uncertain} "
          f"draft pages skipped={len(pages) - len(ready)} invalid pages={failed}")
    return 1 if failed or not ready else 0


if __name__ == "__main__":
    sys.exit(main())
