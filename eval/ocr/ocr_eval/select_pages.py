import argparse
import json
import shutil
import sys

from ocr_eval.paths import DATA_DIR, PAGES_DIR, VOLUMES_DIR


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", required=True)
    parser.add_argument("--source", required=True, help="device path the volume came from")
    parser.add_argument("--selection", required=True, help="JSON list of {file, reason}")
    args = parser.parse_args()

    entries = json.loads(args.selection)
    PAGES_DIR.mkdir(parents=True, exist_ok=True)
    pages = []
    for entry in entries:
        source = next((VOLUMES_DIR / args.name).rglob(entry["file"]), None)
        if source is None:
            raise SystemExit(f"no match for {entry['file']!r} in {VOLUMES_DIR / args.name}")
        number = int(source.stem.split("_")[-1])
        page = f"{args.name}_p{number:03d}"
        shutil.copyfile(source, PAGES_DIR / f"{page}.jpg")
        pages.append({"page": page, "file": entry["file"], "reason": entry["reason"]})
    (DATA_DIR / "selection.json").write_text(
        json.dumps({"volume": args.name, "source": args.source, "pages": pages}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"selected {len(pages)} pages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
