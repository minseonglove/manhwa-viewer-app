import argparse
import subprocess
import sys
import zipfile

from PIL import Image, ImageDraw

from ocr_eval.paths import REVIEW_DIR, VOLUMES_DIR

THUMB_W, THUMB_H = 225, 320
COLUMNS, ROWS = 6, 3
LABEL_H = 22


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-path", required=True)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()

    VOLUMES_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = VOLUMES_DIR / f"{args.name}.zip"
    subprocess.run(["adb", "pull", args.device_path, str(zip_path)], check=True)

    volume_dir = VOLUMES_DIR / args.name
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(volume_dir)
    images = sorted(p for p in volume_dir.rglob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png"})
    print(f"extracted {len(images)} images to {volume_dir}")

    contact_dir = REVIEW_DIR / "contact"
    contact_dir.mkdir(parents=True, exist_ok=True)
    per_sheet = COLUMNS * ROWS
    for sheet_index in range(0, len(images), per_sheet):
        sheet = Image.new("RGB", (COLUMNS * THUMB_W, ROWS * (THUMB_H + LABEL_H)), "white")
        draw = ImageDraw.Draw(sheet)
        for k, path in enumerate(images[sheet_index:sheet_index + per_sheet]):
            thumb = Image.open(path).convert("RGB")
            thumb.thumbnail((THUMB_W, THUMB_H))
            x, y = (k % COLUMNS) * THUMB_W, (k // COLUMNS) * (THUMB_H + LABEL_H)
            sheet.paste(thumb, (x, y + LABEL_H))
            draw.text((x + 4, y + 4), path.name, fill="red")
        sheet.save(contact_dir / f"{args.name}_sheet_{sheet_index // per_sheet:02d}.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
