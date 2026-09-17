from __future__ import annotations

from dataclasses import dataclass
from collections.abc import Iterable


def edit_distance(a: str, b: str) -> int:
    if len(a) < len(b):
        a, b = b, a
    previous = list(range(len(b) + 1))
    for i, char_a in enumerate(a, start=1):
        current = [i]
        for j, char_b in enumerate(b, start=1):
            current.append(min(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + (char_a != char_b),
            ))
        previous = current
    return previous[-1]


@dataclass(frozen=True)
class CerTotals:
    errors: int
    ref_chars: int
    items: int
    exact: int

    @property
    def cer(self) -> float:
        return self.errors / self.ref_chars if self.ref_chars else 0.0

    @property
    def exact_rate(self) -> float:
        return self.exact / self.items if self.items else 0.0

    @staticmethod
    def combine(totals: Iterable[CerTotals]) -> CerTotals:
        errors = ref_chars = items = exact = 0
        for total in totals:
            errors += total.errors
            ref_chars += total.ref_chars
            items += total.items
            exact += total.exact
        return CerTotals(errors, ref_chars, items, exact)
