from __future__ import annotations

from dataclasses import dataclass

from ocr_eval.cer import CerTotals, edit_distance
from ocr_eval.gt import GtItem, GtPage
from ocr_eval.textnorm import NORMALIZERS

MATCH_THRESHOLD = 0.5

Box = tuple[int, int, int, int]


@dataclass(frozen=True)
class PredBlock:
    bbox: Box
    text: str


@dataclass(frozen=True)
class PipelineGroup:
    page: str
    items: tuple[GtItem, ...]
    blocks: tuple[PredBlock, ...]
    reference: str
    hypothesis: str


@dataclass(frozen=True)
class PagePipelineResult:
    page: str
    ms: int | None
    groups: tuple[PipelineGroup, ...]
    missed: tuple[GtItem, ...]
    false_positives: tuple[PredBlock, ...]
    missing_page: bool


@dataclass(frozen=True)
class PipelineSummary:
    gt_items: int
    detected_items: int
    pred_blocks: int
    false_positive_blocks: int
    matched: CerTotals
    overall: CerTotals

    @property
    def recall(self) -> float:
        return self.detected_items / self.gt_items if self.gt_items else 0.0


def overlap_ratio(a: Box, b: Box) -> float:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    inter_w = max(0, min(ax + aw, bx + bw) - max(ax, bx))
    inter_h = max(0, min(ay + ah, by + bh) - max(ay, by))
    smaller = min(aw * ah, bw * bh)
    return inter_w * inter_h / smaller if smaller > 0 else 0.0


def _reading_key(box: Box, horizontal: bool) -> tuple[int, int]:
    x, y, w, _ = box
    return (y, x) if horizontal else (-(x + w), y)


def _find(parent: list[int], i: int) -> int:
    while parent[i] != i:
        parent[i] = parent[parent[i]]
        i = parent[i]
    return i


def match_page(page: GtPage, prediction: dict | None) -> PagePipelineResult:
    blocks = [
        PredBlock(bbox=tuple(int(v) for v in block["bbox"]), text=block["text"])
        for block in (prediction or {}).get("blocks", [])
    ]
    items = list(page.items)
    parent = list(range(len(items) + len(blocks)))
    linked: set[int] = set()
    for i, item in enumerate(items):
        for j, block in enumerate(blocks):
            if overlap_ratio(item.bbox, block.bbox) >= MATCH_THRESHOLD:
                parent[_find(parent, i)] = _find(parent, len(items) + j)
                linked.update((i, len(items) + j))

    components: dict[int, list[int]] = {}
    for node in sorted(linked):
        components.setdefault(_find(parent, node), []).append(node)

    groups = []
    for nodes in components.values():
        group_items = [items[n] for n in nodes if n < len(items)]
        group_blocks = [blocks[n - len(items)] for n in nodes if n >= len(items)]
        horizontal = all(item.orientation == "horizontal" for item in group_items)
        group_items.sort(key=lambda item: _reading_key(item.bbox, horizontal))
        group_blocks.sort(key=lambda block: _reading_key(block.bbox, horizontal))
        groups.append(PipelineGroup(
            page=page.page,
            items=tuple(group_items),
            blocks=tuple(group_blocks),
            reference="".join(item.text for item in group_items),
            hypothesis="".join(block.text for block in group_blocks),
        ))

    return PagePipelineResult(
        page=page.page,
        ms=(prediction or {}).get("ms"),
        groups=tuple(groups),
        missed=tuple(item for i, item in enumerate(items) if i not in linked),
        false_positives=tuple(block for j, block in enumerate(blocks) if len(items) + j not in linked),
        missing_page=prediction is None,
    )


def _group_totals(group: PipelineGroup, mode: str) -> CerTotals:
    normalize = NORMALIZERS[mode]
    ref = normalize(group.reference)
    hyp = normalize(group.hypothesis)
    errors = edit_distance(ref, hyp)
    return CerTotals(errors=errors, ref_chars=len(ref), items=1, exact=int(errors == 0))


def summarize_pipeline(
    results: list[PagePipelineResult],
    mode: str,
    categories: tuple[str, ...] | None,
) -> PipelineSummary:
    def included(item: GtItem) -> bool:
        return categories is None or item.category in categories

    normalize = NORMALIZERS[mode]
    gt_items = detected = pred_blocks = false_positives = 0
    matched: list[CerTotals] = []
    missed: list[CerTotals] = []
    for result in results:
        pred_blocks += sum(len(g.blocks) for g in result.groups) + len(result.false_positives)
        false_positives += len(result.false_positives)
        for group in result.groups:
            members = [item for item in group.items if included(item)]
            gt_items += len(members)
            detected += len(members)
            # 연결 요소 단위로 채점한다: 앱 블록 하나가 여러 종류를 합쳐 읽으면 텍스트를 종류별로 나눌 수 없다.
            if members and any(not item.unreadable for item in group.items):
                matched.append(_group_totals(group, mode))
        for item in result.missed:
            if not included(item):
                continue
            gt_items += 1
            if not item.unreadable:
                ref_chars = len(normalize(item.text))
                missed.append(CerTotals(errors=ref_chars, ref_chars=ref_chars, items=1, exact=0))

    matched_totals = CerTotals.combine(matched)
    return PipelineSummary(
        gt_items=gt_items,
        detected_items=detected,
        pred_blocks=pred_blocks,
        false_positive_blocks=false_positives,
        matched=matched_totals,
        overall=CerTotals.combine([matched_totals, *missed]),
    )
