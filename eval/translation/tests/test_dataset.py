import json

from translation_eval.dataset import load_pages, latest_recognition_run


def _write_gt(tmp_path, name, items):
    (tmp_path / "gt").mkdir(exist_ok=True)
    (tmp_path / "gt" / f"{name}.json").write_text(
        json.dumps({"page": name, "width": 1, "height": 1, "items": items}, ensure_ascii=False), encoding="utf-8"
    )


def test_load_pages_skips_unreadable_and_joins_ocr(tmp_path):
    _write_gt(
        tmp_path,
        "p1",
        [
            {"id": "t01", "bbox": [0, 0, 1, 1], "category": "bubble", "orientation": "vertical", "text": "はっ", "uncertain": False, "unreadable": False},
            {"id": "t02", "bbox": [0, 0, 1, 1], "category": "sfx", "orientation": "horizontal", "text": "", "uncertain": False, "unreadable": True},
            {"id": "t03", "bbox": [0, 0, 1, 1], "category": "background", "orientation": "horizontal", "text": "ポリス", "uncertain": True, "unreadable": False},
        ],
    )
    run = tmp_path / "run"
    run.mkdir()
    (run / "recognition.json").write_text(json.dumps({"p1/t01": {"text": "は", "ms": 1}}), encoding="utf-8")

    pages = load_pages(tmp_path / "gt", ocr_run=run)

    assert [p.name for p in pages] == ["p1"]
    items = pages[0].items
    assert [i.id for i in items] == ["t01", "t03"]
    assert items[0].text("gt") == "はっ" and items[0].text("ocr") == "は"
    assert items[1].ocr is None and items[1].uncertain is True


def test_load_pages_filters_by_category_and_page(tmp_path):
    _write_gt(tmp_path, "p1", [{"id": "t01", "bbox": [0, 0, 1, 1], "category": "sfx", "orientation": "horizontal", "text": "ドン", "uncertain": False, "unreadable": False}])
    _write_gt(tmp_path, "p2", [{"id": "t01", "bbox": [0, 0, 1, 1], "category": "bubble", "orientation": "vertical", "text": "え", "uncertain": False, "unreadable": False}])

    assert [p.name for p in load_pages(tmp_path / "gt", categories=("bubble",))] == ["p2"]
    assert [p.name for p in load_pages(tmp_path / "gt", pages={"p1"})] == ["p1"]


def test_latest_recognition_run_picks_newest_with_recognition(tmp_path):
    (tmp_path / "20260101-000000-a").mkdir()
    (tmp_path / "20260102-000000-b").mkdir()
    (tmp_path / "20260101-000000-a" / "recognition.json").write_text("{}")
    assert latest_recognition_run(tmp_path).name == "20260101-000000-a"
    assert latest_recognition_run(tmp_path / "missing") is None
