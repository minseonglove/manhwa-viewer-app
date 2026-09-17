import re
import unicodedata
from collections.abc import Callable

_WHITESPACE = re.compile(r"\s+")
# NFKC 가 "…" 를 "..." 로, "‥" 를 ".." 로 풀기 때문에 NFKC 이후 점 연속을 하나로 모은다.
_ELLIPSIS = re.compile(r"[.・]{2,}")
_WAVE = str.maketrans({"〜": "~", "～": "~"})
_DECORATIVE = re.compile(r"[♡♥❤★☆♪♫♬◆◇●○◎■□▲△▼▽※]")


def strict(text: str) -> str:
    return _WHITESPACE.sub("", text)


def lenient(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text)
    normalized = _WHITESPACE.sub("", normalized)
    normalized = normalized.translate(_WAVE)
    normalized = _ELLIPSIS.sub("…", normalized)
    return _DECORATIVE.sub("", normalized)


NORMALIZERS: dict[str, Callable[[str], str]] = {"strict": strict, "lenient": lenient}
