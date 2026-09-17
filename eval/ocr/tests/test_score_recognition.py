import pytest

from ocr_eval.cer import CerTotals
from ocr_eval.gt import GtItem, GtPage
from ocr_eval.score_recognition import score_recognition, summarize_recognition, unknown_prediction_keys


def _page():
    return GtPage(page="p1", width=100, height=100, items=(
        GtItem("t01", (0, 0, 10, 10), "bubble", "vertical", "あいう"),
        GtItem("t02", (0, 0, 10, 10), "narration", "horizontal", "ＯＫ…"),
        GtItem("t03", (0, 0, 10, 10), "sfx", "vertical", "", unreadable=True),
        GtItem("t04", (0, 0, 10, 10), "aside", "vertical", "かき"),
    ))


def test_perfect_predictions_score_zero():
    predictions = {
        "p1/t01": {"text": "あいう", "ms": 10},
        "p1/t02": {"text": "ＯＫ…", "ms": 12},
        "p1/t03": {"text": "ドン", "ms": 9},
        "p1/t04": {"text": "かき", "ms": 11},
    }
    records = score_recognition([_page()], predictions)
    assert [r.key for r in records] == ["p1/t01", "p1/t02", "p1/t04"]  # unreadable excluded
    summary = summarize_recognition(records, "lenient")
    assert summary["all"] == CerTotals(errors=0, ref_chars=8, items=3, exact=3)


def test_missing_predictions_count_as_empty_output():
    records = score_recognition([_page()], {})
    assert all(r.missing for r in records)
    summary = summarize_recognition(records, "strict")
    assert summary["all"].cer == pytest.approx(1.0)
    assert summary["all"].exact == 0


def test_strict_and_lenient_differ_on_notation():
    predictions = {"p1/t02": {"text": "OK...", "ms": 1}}
    records = score_recognition([_page()], predictions)
    by_key = {r.key: r for r in records}
    assert by_key["p1/t02"].totals["lenient"].errors == 0
    assert by_key["p1/t02"].totals["strict"].errors > 0


def test_summary_groups():
    predictions = {
        "p1/t01": {"text": "あいx", "ms": 1},
        "p1/t02": {"text": "OK…", "ms": 1},
        "p1/t04": {"text": "かき", "ms": 1},
    }
    summary = summarize_recognition(score_recognition([_page()], predictions), "lenient")
    assert summary["primary"] == CerTotals(errors=1, ref_chars=6, items=2, exact=1)
    assert summary["category:aside"] == CerTotals(errors=0, ref_chars=2, items=1, exact=1)
    assert summary["orientation:horizontal"].items == 1
    assert "category:sfx" not in summary


def test_unknown_prediction_keys():
    predictions = {"p1/t01": {"text": "", "ms": 0}, "p9/t01": {"text": "", "ms": 0}}
    assert unknown_prediction_keys([_page()], predictions) == ["p9/t01"]
