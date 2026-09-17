import pytest

from ocr_eval.cer import CerTotals, edit_distance


@pytest.mark.parametrize("a,b,expected", [
    ("kitten", "sitting", 3),
    ("", "abc", 3),
    ("abc", "", 3),
    ("こんにちは", "こんばんは", 2),
    ("同じ", "同じ", 0),
])
def test_edit_distance(a, b, expected):
    assert edit_distance(a, b) == expected


def test_totals_properties():
    totals = CerTotals(errors=2, ref_chars=5, items=2, exact=1)
    assert totals.cer == pytest.approx(0.4)
    assert totals.exact_rate == pytest.approx(0.5)


def test_empty_totals_do_not_divide_by_zero():
    assert CerTotals(0, 0, 0, 0).cer == 0.0
    assert CerTotals(0, 0, 0, 0).exact_rate == 0.0


def test_combine_sums_fields():
    combined = CerTotals.combine([CerTotals(1, 4, 1, 0), CerTotals(0, 3, 1, 1)])
    assert combined == CerTotals(errors=1, ref_chars=7, items=2, exact=1)
