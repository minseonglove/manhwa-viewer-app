import pytest

from ocr_eval.proposals import Proposal, iou, merge_text_proposals, xyxy_to_xywh


def test_xyxy_to_xywh_rounds():
    assert xyxy_to_xywh(10.4, 20.6, 110.6, 220.4) == (10, 21, 101, 199)


def test_iou():
    assert iou((0, 0, 10, 10), (0, 0, 10, 10)) == pytest.approx(1.0)
    assert iou((0, 0, 10, 10), (5, 0, 10, 10)) == pytest.approx(50 / 150)
    assert iou((0, 0, 10, 10), (20, 20, 5, 5)) == 0.0


def test_overlapping_proposals_merge_keeping_highest_score_box():
    merged = merge_text_proposals([
        Proposal("koharu", "text", 0.9, (100, 100, 60, 200)),
        Proposal("ogkalu", "text_bubble", 0.8, (102, 98, 58, 205)),
    ])
    assert len(merged) == 1
    assert merged[0]["bbox"] == [100, 100, 60, 200]
    assert merged[0]["label"] == "text"
    assert {s["source"] for s in merged[0]["sources"]} == {"koharu", "ogkalu"}


def test_non_overlapping_proposals_numbered_in_reading_order():
    merged = merge_text_proposals([
        Proposal("koharu", "text", 0.9, (100, 100, 60, 200)),   # left column
        Proposal("ogkalu", "text_free", 0.7, (500, 120, 60, 200)),  # right column -> first
        Proposal("koharu", "onomatopoeia", 0.4, (500, 600, 60, 80)),  # same right edge, lower -> second
    ])
    assert [(m["n"], m["bbox"][0], m["bbox"][1]) for m in merged] == [(1, 500, 120), (2, 500, 600), (3, 100, 100)]
