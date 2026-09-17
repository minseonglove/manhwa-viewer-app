from __future__ import annotations

from dataclasses import dataclass

Box = tuple[int, int, int, int]

TEXT_LABELS = {("koharu", "text"), ("koharu", "onomatopoeia"), ("ogkalu", "text_bubble"), ("ogkalu", "text_free")}
BUBBLE_LABELS = {("koharu", "bubble"), ("ogkalu", "bubble")}


@dataclass(frozen=True)
class Proposal:
    source: str
    label: str
    score: float
    bbox: Box


def xyxy_to_xywh(x1: float, y1: float, x2: float, y2: float) -> Box:
    # 검출기 출력이 numpy 스칼라여도 JSON 직렬화 가능한 int 로 만든다.
    left, top, right, bottom = (int(round(float(v))) for v in (x1, y1, x2, y2))
    return left, top, right - left, bottom - top


def iou(a: Box, b: Box) -> float:
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    inter_w = max(0, min(ax + aw, bx + bw) - max(ax, bx))
    inter_h = max(0, min(ay + ah, by + bh) - max(ay, by))
    inter = inter_w * inter_h
    union = aw * ah + bw * bh - inter
    return inter / union if union > 0 else 0.0


def merge_text_proposals(items: list[Proposal], iou_threshold: float = 0.5) -> list[dict]:
    parent = list(range(len(items)))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i in range(len(items)):
        for j in range(i + 1, len(items)):
            if iou(items[i].bbox, items[j].bbox) >= iou_threshold:
                parent[find(i)] = find(j)

    clusters: dict[int, list[Proposal]] = {}
    for i, item in enumerate(items):
        clusters.setdefault(find(i), []).append(item)

    merged = []
    for members in clusters.values():
        best = max(members, key=lambda p: p.score)
        merged.append({
            "bbox": list(best.bbox),
            "label": best.label,
            "score": round(best.score, 3),
            "sources": [
                {"source": p.source, "label": p.label, "score": round(p.score, 3), "bbox": list(p.bbox)}
                for p in sorted(members, key=lambda p: -p.score)
            ],
        })
    merged.sort(key=lambda m: (-(m["bbox"][0] + m["bbox"][2]), m["bbox"][1]))
    for n, entry in enumerate(merged, start=1):
        entry["n"] = n
    return merged
