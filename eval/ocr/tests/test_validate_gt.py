from pathlib import Path

from ocr_eval.gt import GtItem, GtPage
from ocr_eval.validate_gt import check_filenames


def _page(name):
    return GtPage(page=name, width=100, height=100, items=(
        GtItem("t01", (0, 0, 10, 10), "bubble", "vertical", "あ"),
    ))


def test_matching_stem_has_no_errors():
    assert check_filenames([Path("p1.json")], [_page("p1")]) == []


def test_mismatched_stem_is_reported():
    errors = check_filenames([Path("p1.json")], [_page("p2")])
    assert len(errors) == 1
    assert "p1.json" in errors[0]
    assert "p2" in errors[0]


def test_duplicate_page_name_is_reported():
    errors = check_filenames([Path("a.json"), Path("b.json")], [_page("p1"), _page("p1")])
    assert any("declared by multiple files" in e for e in errors)
    assert any("a.json" in e and "b.json" in e for e in errors)
