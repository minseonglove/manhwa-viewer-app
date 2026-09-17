import pytest

from ocr_eval.cer import CerTotals
from ocr_eval.gt import PRIMARY_CATEGORIES, GtItem, GtPage
from ocr_eval.score_pipeline import match_page, overlap_ratio, summarize_pipeline


def _page(*items):
    return GtPage(page="p1", width=1000, height=1000, items=tuple(items))


def _blocks(*blocks):
    return {"ms": 100, "blocks": [{"bbox": list(b), "text": t} for b, t in blocks]}


def test_overlap_ratio_uses_smaller_area():
    assert overlap_ratio((0, 0, 100, 100), (50, 0, 50, 100)) == pytest.approx(1.0)
    assert overlap_ratio((0, 0, 100, 100), (200, 200, 10, 10)) == 0.0


def test_exact_detection():
    page = _page(GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう"))
    result = match_page(page, _blocks(((95, 95, 70, 210), "あいう")))
    summary = summarize_pipeline([result], "lenient", None)
    assert summary.recall == 1.0
    assert summary.false_positive_blocks == 0
    assert summary.matched == CerTotals(errors=0, ref_chars=3, items=1, exact=1)


def test_split_vertical_columns_are_joined_right_to_left():
    page = _page(GtItem("t01", (100, 100, 200, 300), "bubble", "vertical", "あいうえお"))
    result = match_page(page, _blocks(
        ((100, 100, 100, 300), "えお"),   # left column read second
        ((200, 100, 100, 300), "あいう"),  # right column read first
    ))
    assert result.groups[0].hypothesis == "あいうえお"
    assert summarize_pipeline([result], "lenient", None).matched.errors == 0


def test_merged_items_are_joined_in_reading_order():
    page = _page(
        GtItem("t01", (200, 100, 100, 300), "bubble", "vertical", "かき"),
        GtItem("t02", (100, 100, 100, 300), "bubble", "vertical", "くけ"),
    )
    result = match_page(page, _blocks(((100, 100, 200, 300), "かきくけ")))
    assert len(result.groups) == 1
    assert result.groups[0].reference == "かきくけ"
    assert summarize_pipeline([result], "lenient", None).matched.errors == 0


def test_horizontal_group_reads_top_to_bottom():
    page = _page(
        GtItem("t01", (100, 100, 300, 40), "background", "horizontal", "上の行"),
        GtItem("t02", (100, 150, 150, 40), "background", "horizontal", "下"),
    )
    result = match_page(page, _blocks(((90, 90, 320, 110), "上の行下")))
    assert result.groups[0].reference == "上の行下"


def test_missed_item_counts_fully_in_overall_cer():
    page = _page(
        GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう"),
        GtItem("t02", (500, 500, 60, 200), "bubble", "vertical", "かきくけ"),
    )
    result = match_page(page, _blocks(((100, 100, 60, 200), "あいう")))
    summary = summarize_pipeline([result], "lenient", None)
    assert [item.id for item in result.missed] == ["t02"]
    assert summary.recall == pytest.approx(0.5)
    assert summary.matched == CerTotals(errors=0, ref_chars=3, items=1, exact=1)
    assert summary.overall == CerTotals(errors=4, ref_chars=7, items=2, exact=1)


def test_false_positive_block():
    page = _page(GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あ"))
    result = match_page(page, _blocks(((100, 100, 60, 200), "あ"), ((800, 800, 50, 50), "ゴミ")))
    summary = summarize_pipeline([result], "lenient", None)
    assert summary.false_positive_blocks == 1
    assert summary.pred_blocks == 2


def test_unreadable_only_group_is_detected_but_not_scored():
    page = _page(GtItem("t01", (100, 100, 200, 200), "sfx", "vertical", "", unreadable=True))
    result = match_page(page, _blocks(((100, 100, 200, 200), "ドン")))
    summary = summarize_pipeline([result], "lenient", None)
    assert summary.detected_items == 1
    assert summary.matched == CerTotals(0, 0, 0, 0)


def test_missing_page_prediction_marks_all_missed():
    page = _page(GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう"))
    result = match_page(page, None)
    assert result.missing_page
    assert summarize_pipeline([result], "lenient", None).overall.cer == pytest.approx(1.0)


def test_category_filter():
    page = _page(
        GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう"),
        GtItem("t02", (500, 500, 60, 200), "sfx", "vertical", "ドン"),
    )
    result = match_page(page, _blocks(((100, 100, 60, 200), "あいう")))
    primary = summarize_pipeline([result], "lenient", PRIMARY_CATEGORIES)
    assert primary.gt_items == 1
    assert primary.recall == 1.0


def test_category_filter_scores_whole_component_when_categories_mix():
    page = _page(
        GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう"),
        GtItem("t02", (170, 100, 60, 200), "sfx", "vertical", "ドン"),
    )
    result = match_page(page, _blocks(((100, 100, 130, 200), "ドンあいう")))
    primary = summarize_pipeline([result], "lenient", PRIMARY_CATEGORIES)
    assert primary.gt_items == 1
    assert primary.detected_items == 1
    assert primary.matched == CerTotals(errors=0, ref_chars=5, items=1, exact=1)
