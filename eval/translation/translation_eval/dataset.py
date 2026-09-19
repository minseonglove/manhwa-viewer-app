from __future__ import annotations

import base64
import json
from dataclasses import dataclass
from pathlib import Path

READABLE_CATEGORIES = ("bubble", "narration", "aside", "sfx", "background")


@dataclass(frozen=True)
class Item:
    page: str
    id: str
    category: str
    orientation: str
    gt: str
    ocr: str | None
    uncertain: bool

    def text(self, source: str) -> str | None:
        if source == "gt":
            return self.gt
        if source == "ocr":
            return self.ocr
        raise ValueError(f"unknown source: {source}")


@dataclass(frozen=True)
class Page:
    name: str
    items: tuple[Item, ...]


def load_ocr_texts(run_dir: Path | None) -> dict[str, str]:
    if run_dir is None:
        return {}
    data = json.loads((run_dir / "recognition.json").read_text(encoding="utf-8"))
    return {key: entry["text"] for key, entry in data.items()}


def load_pages(
    gt_dir: Path,
    ocr_run: Path | None = None,
    categories: tuple[str, ...] = READABLE_CATEGORIES,
    pages: set[str] | None = None,
) -> list[Page]:
    ocr_texts = load_ocr_texts(ocr_run)
    result: list[Page] = []
    for path in sorted(gt_dir.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        name = data["page"]
        if pages is not None and name not in pages:
            continue
        items = []
        for entry in data["items"]:
            if entry.get("unreadable") or not entry["text"]:
                continue
            if entry["category"] not in categories:
                continue
            items.append(
                Item(
                    page=name,
                    id=entry["id"],
                    category=entry["category"],
                    orientation=entry["orientation"],
                    gt=entry["text"],
                    ocr=ocr_texts.get(f"{name}/{entry['id']}"),
                    uncertain=bool(entry.get("uncertain")),
                )
            )
        if items:
            result.append(Page(name=name, items=tuple(items)))
    return result


def latest_recognition_run(runs_dir: Path) -> Path | None:
    candidates = [p for p in runs_dir.iterdir() if (p / "recognition.json").exists()] if runs_dir.exists() else []
    if not candidates:
        return None
    return max(candidates, key=lambda p: p.name)


def page_image_data_url(pages_dir: Path, page: str) -> str:
    path = pages_dir / f"{page}.jpg"
    return "data:image/jpeg;base64," + base64.b64encode(path.read_bytes()).decode("ascii")
