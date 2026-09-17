from __future__ import annotations

from dataclasses import dataclass

from ocr_eval.cer import CerTotals, edit_distance
from ocr_eval.gt import CATEGORIES, ORIENTATIONS, PRIMARY_CATEGORIES, GtItem, GtPage
from ocr_eval.textnorm import NORMALIZERS


@dataclass(frozen=True)
class RecognitionRecord:
    key: str
    page: str
    item: GtItem
    hypothesis: str
    missing: bool
    ms: int | None
    totals: dict[str, CerTotals]


def _item_totals(reference: str, hypothesis: str) -> dict[str, CerTotals]:
    result = {}
    for mode, normalize in NORMALIZERS.items():
        ref = normalize(reference)
        hyp = normalize(hypothesis)
        errors = edit_distance(ref, hyp)
        result[mode] = CerTotals(errors=errors, ref_chars=len(ref), items=1, exact=int(errors == 0))
    return result


def score_recognition(pages: list[GtPage], predictions: dict[str, dict]) -> list[RecognitionRecord]:
    records = []
    for page in pages:
        for item in page.items:
            if item.unreadable:
                continue
            key = f"{page.page}/{item.id}"
            prediction = predictions.get(key)
            hypothesis = prediction["text"] if prediction else ""
            records.append(RecognitionRecord(
                key=key,
                page=page.page,
                item=item,
                hypothesis=hypothesis,
                missing=prediction is None,
                ms=prediction.get("ms") if prediction else None,
                totals=_item_totals(item.text, hypothesis),
            ))
    return records


def summarize_recognition(records: list[RecognitionRecord], mode: str) -> dict[str, CerTotals]:
    groups: dict[str, list[RecognitionRecord]] = {
        "primary": [r for r in records if r.item.category in PRIMARY_CATEGORIES],
        "all": list(records),
    }
    for category in CATEGORIES:
        groups[f"category:{category}"] = [r for r in records if r.item.category == category]
    for orientation in ORIENTATIONS:
        groups[f"orientation:{orientation}"] = [r for r in records if r.item.orientation == orientation]
    return {
        name: CerTotals.combine(r.totals[mode] for r in members)
        for name, members in groups.items()
        if members
    }


def unknown_prediction_keys(pages: list[GtPage], predictions: dict[str, dict]) -> list[str]:
    known = {f"{page.page}/{item.id}" for page in pages for item in page.items}
    return sorted(key for key in predictions if key not in known)
