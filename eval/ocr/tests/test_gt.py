import json

from ocr_eval.gt import GtItem, GtPage, load_pages, page_to_dict, parse_page, ready_pages, validate_page


def _item(**overrides):
    base = dict(id="t01", bbox=[10, 10, 50, 100], category="bubble", orientation="vertical", text="あいう")
    base.update(overrides)
    return base


def _page(items, **overrides):
    base = dict(page="city01_p012", width=1125, height=1600, items=items)
    base.update(overrides)
    return base


def test_parse_page_reads_fields_and_defaults():
    page = parse_page(_page([_item()]))
    assert page.page == "city01_p012"
    assert page.draft is False
    assert page.items[0] == GtItem(id="t01", bbox=(10, 10, 50, 100), category="bubble",
                                   orientation="vertical", text="あいう")


def test_round_trip_through_dict():
    page = parse_page(_page([_item(uncertain=True, note="n")], draft=True))
    assert parse_page(page_to_dict(page)) == page


def test_valid_page_has_no_errors():
    assert validate_page(parse_page(_page([_item()])), (1125, 1600)) == []


def test_validation_reports_each_problem():
    page = parse_page(_page([
        _item(id="t01", bbox=[1100, 10, 50, 100]),          # out of bounds
        _item(id="t01", category="speech"),                   # duplicate id, bad category
        _item(id="t03", orientation="diagonal"),              # bad orientation
        _item(id="t04", text=""),                             # empty text but readable
        _item(id="t05", text="ドン", unreadable=True),        # unreadable must be empty
        _item(id="t06", bbox=[10, 10, 0, 100]),               # zero width
    ]))
    errors = validate_page(page, (1125, 1600))
    joined = "\n".join(errors)
    assert "t01: bbox out of bounds" in joined
    assert "duplicate id t01" in joined
    assert "t01: invalid category speech" in joined
    assert "t03: invalid orientation diagonal" in joined
    assert "t04: empty text" in joined
    assert "t05: unreadable item must have empty text" in joined
    assert "t06: non-positive bbox size" in joined


def test_validation_reports_image_size_mismatch():
    errors = validate_page(parse_page(_page([_item()])), (1000, 1600))
    assert errors == ["page size 1125x1600 != image size 1000x1600"]


def test_load_pages_sorts_and_ready_pages_skips_drafts(tmp_path):
    (tmp_path / "b.json").write_text(json.dumps(_page([_item()], page="b")), encoding="utf-8")
    (tmp_path / "a.json").write_text(json.dumps(_page([_item()], page="a", draft=True)), encoding="utf-8")
    pages = load_pages(tmp_path)
    assert [p.page for p in pages] == ["a", "b"]
    assert [p.page for p in ready_pages(pages)] == ["b"]
