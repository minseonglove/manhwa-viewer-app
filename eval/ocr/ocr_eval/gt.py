from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

CATEGORIES = ("bubble", "narration", "aside", "sfx", "background")
ORIENTATIONS = ("vertical", "horizontal")
PRIMARY_CATEGORIES = ("bubble", "narration")


@dataclass(frozen=True)
class GtItem:
    id: str
    bbox: tuple[int, int, int, int]
    category: str
    orientation: str
    text: str
    uncertain: bool = False
    unreadable: bool = False
    note: str = ""


@dataclass(frozen=True)
class GtPage:
    page: str
    width: int
    height: int
    items: tuple[GtItem, ...]
    draft: bool = False


def parse_page(data: dict) -> GtPage:
    items = tuple(
        GtItem(
            id=item["id"],
            bbox=tuple(int(v) for v in item["bbox"]),
            category=item["category"],
            orientation=item["orientation"],
            text=item["text"],
            uncertain=bool(item.get("uncertain", False)),
            unreadable=bool(item.get("unreadable", False)),
            note=item.get("note", ""),
        )
        for item in data["items"]
    )
    return GtPage(
        page=data["page"],
        width=int(data["width"]),
        height=int(data["height"]),
        items=items,
        draft=bool(data.get("draft", False)),
    )


def page_to_dict(page: GtPage) -> dict:
    data = {
        "page": page.page,
        "width": page.width,
        "height": page.height,
        "items": [
            {
                "id": item.id,
                "bbox": list(item.bbox),
                "category": item.category,
                "orientation": item.orientation,
                "text": item.text,
                "uncertain": item.uncertain,
                "unreadable": item.unreadable,
                "note": item.note,
            }
            for item in page.items
        ],
    }
    if page.draft:
        data["draft"] = True
    return data


def load_page(path: Path) -> GtPage:
    return parse_page(json.loads(path.read_text(encoding="utf-8")))


def load_pages(gt_dir: Path) -> list[GtPage]:
    return [load_page(path) for path in sorted(gt_dir.glob("*.json"))]


def ready_pages(pages: list[GtPage]) -> list[GtPage]:
    return [page for page in pages if not page.draft]


def validate_page(page: GtPage, image_size: tuple[int, int] | None) -> list[str]:
    errors: list[str] = []
    if image_size is not None and image_size != (page.width, page.height):
        errors.append(f"page size {page.width}x{page.height} != image size {image_size[0]}x{image_size[1]}")
    seen: set[str] = set()
    for item in page.items:
        if item.id in seen:
            errors.append(f"duplicate id {item.id}")
        seen.add(item.id)
        x, y, w, h = item.bbox
        if w <= 0 or h <= 0:
            errors.append(f"{item.id}: non-positive bbox size")
        elif x < 0 or y < 0 or x + w > page.width or y + h > page.height:
            errors.append(f"{item.id}: bbox out of bounds")
        if item.category not in CATEGORIES:
            errors.append(f"{item.id}: invalid category {item.category}")
        if item.orientation not in ORIENTATIONS:
            errors.append(f"{item.id}: invalid orientation {item.orientation}")
        if item.unreadable and item.text:
            errors.append(f"{item.id}: unreadable item must have empty text")
        if not item.unreadable and not item.text:
            errors.append(f"{item.id}: empty text")
    return errors
