import argparse
import json
import sys

from PIL import Image, ImageDraw

from ocr_eval.crop import crop_item
from ocr_eval.gt import load_page
from ocr_eval.overlay import draw_grid
from ocr_eval.paths import GT_DIR, PAGES_DIR, PROPOSALS_DIR, REVIEW_DIR
from ocr_eval.tiles import pack

SHEET_MAX_WIDTH = 1000
SHEET_MAX_HEIGHT = 1100
TILE_MAX_SIDE = 1000
LABEL_H = 20
CONTEXT_PX = 60


def _tile(crop: Image.Image, label: str, scale: float) -> Image.Image:
    factor = min(scale, TILE_MAX_SIDE / max(crop.width, crop.height))
    resized = crop.resize((max(1, round(crop.width * factor)), max(1, round(crop.height * factor))), Image.Resampling.LANCZOS)
    tile = Image.new("RGB", (max(resized.width, 170), resized.height + LABEL_H), "white")
    tile.paste(resized, (0, LABEL_H))
    ImageDraw.Draw(tile).text((2, 3), f"{label} x{factor:.1f}", fill=(230, 0, 0))
    return tile


def _save_sheets(tiles: list[Image.Image], prefix: str) -> None:
    out_dir = REVIEW_DIR / "crops"
    out_dir.mkdir(parents=True, exist_ok=True)
    sheet_index = 0
    start = 0
    while start < len(tiles):
        positions = pack([t.size for t in tiles[start:]], SHEET_MAX_WIDTH)
        count = next((k for k, (_, y) in enumerate(positions) if k and y + tiles[start + k].height > SHEET_MAX_HEIGHT),
                     len(positions))
        chunk, chunk_positions = tiles[start:start + count], positions[:count]
        height = max(y + t.height for t, (_, y) in zip(chunk, chunk_positions))
        sheet = Image.new("RGB", (SHEET_MAX_WIDTH, height), (235, 235, 235))
        for tile, position in zip(chunk, chunk_positions):
            sheet.paste(tile, position)
        path = out_dir / f"{prefix}_{sheet_index:02d}.png"
        sheet.save(path)
        print(path)
        sheet_index += 1
        start += count


def render_proposals(page: str, image: Image.Image) -> None:
    proposals = json.loads((PROPOSALS_DIR / f"{page}.json").read_text(encoding="utf-8"))
    tiles = [_tile(crop_item(image, tuple(e["bbox"])), f"#{e['n']} {e['label']}", 2.0) for e in proposals["text"]]
    _save_sheets(tiles, f"{page}_proposals")


def render_gt(page: str, image: Image.Image) -> None:
    gt = load_page(GT_DIR / f"{page}.json")
    tiles = [_tile(crop_item(image, item.bbox), f"{item.id} {item.category}", 2.0) for item in gt.items]
    _save_sheets(tiles, f"{page}_gt")


def render_quadrants(page: str, image: Image.Image) -> None:
    gt = load_page(GT_DIR / f"{page}.json")
    canvas = draw_grid(image)
    draw = ImageDraw.Draw(canvas)
    for item in gt.items:
        x, y, w, h = item.bbox
        draw.rectangle([x, y, x + w, y + h], outline=(230, 0, 0), width=2)
        draw.text((x + 2, y + 2), item.id, fill=(230, 0, 0))
    out_dir = REVIEW_DIR / "quadrants"
    out_dir.mkdir(parents=True, exist_ok=True)
    half_w, half_h = canvas.width // 2, canvas.height // 2
    overlap = 80
    boxes = [
        (0, 0, half_w + overlap, half_h + overlap),
        (half_w - overlap, 0, canvas.width, half_h + overlap),
        (0, half_h - overlap, half_w + overlap, canvas.height),
        (half_w - overlap, half_h - overlap, canvas.width, canvas.height),
    ]
    for k, box in enumerate(boxes, start=1):
        region = canvas.crop(box)
        region = region.resize((round(region.width * 1.5), round(region.height * 1.5)), Image.Resampling.LANCZOS)
        path = out_dir / f"{page}_q{k}.png"
        region.save(path)
        print(path)


def render_box(page: str, image: Image.Image, box: tuple[int, int, int, int]) -> None:
    x, y, w, h = box
    left, top = max(0, x - CONTEXT_PX), max(0, y - CONTEXT_PX)
    right, bottom = min(image.width, x + w + CONTEXT_PX), min(image.height, y + h + CONTEXT_PX)
    region = image.crop((left, top, right, bottom)).convert("RGB")
    region = region.resize((region.width * 3, region.height * 3), Image.Resampling.LANCZOS)
    ImageDraw.Draw(region).rectangle(
        [(x - left) * 3, (y - top) * 3, (x - left + w) * 3, (y - top + h) * 3], outline=(230, 0, 0), width=2
    )
    out_dir = REVIEW_DIR / "adhoc"
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"{page}_{x}_{y}_{w}_{h}.png"
    region.save(path)
    print(path)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--page", required=True)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--gt", action="store_true")
    mode.add_argument("--quadrants", action="store_true")
    mode.add_argument("--box", type=int, nargs=4, metavar=("X", "Y", "W", "H"))
    args = parser.parse_args()

    image = Image.open(PAGES_DIR / f"{args.page}.jpg").convert("RGB")
    if args.gt:
        render_gt(args.page, image)
    elif args.quadrants:
        render_quadrants(args.page, image)
    elif args.box:
        render_box(args.page, image, tuple(args.box))
    else:
        render_proposals(args.page, image)
    return 0


if __name__ == "__main__":
    sys.exit(main())
