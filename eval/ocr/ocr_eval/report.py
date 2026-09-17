from __future__ import annotations

from statistics import median

from ocr_eval.cer import CerTotals
from ocr_eval.gt import CATEGORIES, PRIMARY_CATEGORIES, GtPage
from ocr_eval.runs import Run
from ocr_eval.score_pipeline import match_page, summarize_pipeline
from ocr_eval.score_recognition import (
    RecognitionRecord,
    score_recognition,
    summarize_recognition,
    unknown_prediction_keys,
)


def _cell(text: str) -> str:
    return text.replace("|", "\\|") or "∅"


def _pct(value: float) -> str:
    return f"{value * 100:.2f}%"


def _rate(value: float, denominator: int) -> str:
    return "—" if denominator == 0 else _pct(value)


def count_gaps(run: Run, pages: list[GtPage]) -> dict:
    readable_keys = {f"{p.page}/{i.id}" for p in pages for i in p.items if not i.unreadable}
    recognition = run.recognition or {}
    pipeline = run.pipeline or {}
    return {
        "missing_recognition": len(readable_keys - recognition.keys()) if run.recognition is not None else 0,
        "unknown": len(unknown_prediction_keys(pages, recognition)),
        "missing_pipeline": sum(p.page not in pipeline for p in pages) if run.pipeline is not None else 0,
    }


def _denominators(run: Run, pages: list[GtPage], skipped_drafts: int, gt_status: str | None) -> str:
    items = [item for page in pages for item in page.items]
    unreadable = sum(item.unreadable for item in items)
    gaps = count_gaps(run, pages)
    recognition = run.recognition or {}
    pipeline = run.pipeline or {}
    line = (
        f"pages={len(pages)} items={len(items)} readable={len(items) - unreadable} "
        f"unreadable={unreadable} draft pages skipped={skipped_drafts} | "
        f"recognition predictions={len(recognition)} missing={gaps['missing_recognition']} "
        f"unknown={gaps['unknown']} | "
        f"pipeline pages={len(pipeline)} missing={gaps['missing_pipeline']}"
    )
    return f"{line} | gt={gt_status}" if gt_status is not None else line


def _recognition_table(records: list[RecognitionRecord]) -> list[str]:
    lenient = summarize_recognition(records, "lenient")
    strict = summarize_recognition(records, "strict")
    lines = ["| 그룹 | 항목 | CER (lenient) | 완전 일치 (lenient) | CER (strict) |", "|---|---:|---:|---:|---:|"]
    for name, totals in lenient.items():
        lines.append(f"| {name} | {totals.items} | {_rate(totals.cer, totals.ref_chars)} | "
                     f"{_rate(totals.exact_rate, totals.items)} | "
                     f"{_rate(strict[name].cer, strict[name].ref_chars)} |")
    return lines


def render_report(
    run: Run,
    pages: list[GtPage],
    skipped_drafts: int,
    worst_limit: int = 30,
    gt_status: str | None = None,
) -> tuple[str, list[RecognitionRecord]]:
    lines = [_denominators(run, pages, skipped_drafts, gt_status), "", f"# OCR 평가 리포트 — {run.path.name}", ""]
    lines += [f"- {key}: {value}" for key, value in run.meta.items()]
    worst: list[RecognitionRecord] = []

    if run.recognition is not None:
        records = score_recognition(pages, run.recognition)
        timings = [r.ms for r in records if r.ms is not None]
        lines += ["", "## 인식 단독", ""]
        lines += _recognition_table(records)
        uncertain = [r for r in records if r.item.uncertain]
        if uncertain:
            totals = CerTotals.combine(r.totals["lenient"] for r in uncertain)
            lines += ["", f"uncertain 항목(정답 판독이 애매한 항목, 위 표에 포함): {totals.items}개, "
                          f"CER {_pct(totals.cer)}"]
        if timings:
            lines += ["", f"항목당 처리 시간 중앙값: {median(timings):.0f}ms"]
        worst = sorted(
            (r for r in records if r.totals["lenient"].errors > 0),
            key=lambda r: (-r.totals["lenient"].cer, r.key),
        )[:worst_limit]
        lines += ["", f"### 오답 상위 {len(worst)}개 (lenient CER 순)", "",
                  "| 항목 | 종류 | CER | 정답 | 출력 | 크롭 |", "|---|---|---:|---|---|---|"]
        for r in worst:
            category = f"{r.item.category} (uncertain)" if r.item.uncertain else r.item.category
            lines.append(f"| {r.key} | {category} | {_pct(r.totals['lenient'].cer)} | "
                         f"{_cell(r.item.text)} | {_cell(r.hypothesis)} | crops/{r.page}_{r.item.id}.png |")

    if run.pipeline is not None:
        results = [match_page(page, run.pipeline.get(page.page)) for page in pages]
        lines += ["", "## 파이프라인", "",
                  "| 그룹 | 정답 항목 | 검출률 | CER (연결된 것만) | 종합 CER (누락 포함) |",
                  "|---|---:|---:|---:|---:|"]
        present_categories = {item.category for page in pages for item in page.items}
        rows = [("primary", PRIMARY_CATEGORIES), ("all", None)]
        rows += [(f"category:{category}", (category,)) for category in CATEGORIES if category in present_categories]
        for label, categories in rows:
            summary = summarize_pipeline(results, "lenient", categories)
            lines.append(f"| {label} | {summary.gt_items} | {_rate(summary.recall, summary.gt_items)} | "
                         f"{_rate(summary.matched.cer, summary.matched.ref_chars)} | "
                         f"{_rate(summary.overall.cer, summary.overall.ref_chars)} |")
        overall = summarize_pipeline(results, "lenient", None)
        timings = [r.ms for r in results if r.ms is not None]
        lines += ["", f"오검출 블록: {overall.false_positive_blocks} / {overall.pred_blocks}"]
        if timings:
            lines.append(f"페이지당 처리 시간 중앙값: {median(timings):.0f}ms")
        lines += ["", "### 놓친 항목", "", "| 페이지 | 항목 | 종류 | 정답 |", "|---|---|---|---|"]
        for result in results:
            for item in result.missed:
                lines.append(f"| {result.page} | {item.id} | {item.category} | {_cell(item.text)} |")

    return "\n".join(lines) + "\n", worst


def render_compare(run_a: Run, run_b: Run, pages: list[GtPage], skipped_drafts: int, limit: int = 30) -> str:
    items = sum(len(page.items) for page in pages)
    lines = [
        f"pages={len(pages)} items={items} draft pages skipped={skipped_drafts} | "
        f"A recognition predictions={len(run_a.recognition or {})} | "
        f"B recognition predictions={len(run_b.recognition or {})}",
        "",
        f"# 비교 — {run_a.path.name} → {run_b.path.name}",
        "",
    ]
    if run_a.recognition is None or run_b.recognition is None:
        return "\n".join(lines + ["두 실행 모두 recognition.json 이 있어야 비교할 수 있다."]) + "\n"
    records_a = {r.key: r for r in score_recognition(pages, run_a.recognition)}
    records_b = {r.key: r for r in score_recognition(pages, run_b.recognition)}
    summary_a = summarize_recognition(list(records_a.values()), "lenient")
    summary_b = summarize_recognition(list(records_b.values()), "lenient")
    lines += ["| 그룹 | CER A | CER B | 완전 일치 A | 완전 일치 B |", "|---|---:|---:|---:|---:|"]
    for name, totals_a in summary_a.items():
        totals_b = summary_b.get(name, CerTotals(0, 0, 0, 0))
        lines.append(f"| {name} | {_rate(totals_a.cer, totals_a.ref_chars)} | {_rate(totals_b.cer, totals_b.ref_chars)} | "
                     f"{_rate(totals_a.exact_rate, totals_a.items)} | {_rate(totals_b.exact_rate, totals_b.items)} |")

    deltas = [
        (records_b[key].totals["lenient"].errors - records_a[key].totals["lenient"].errors, key)
        for key in records_a
    ]
    improved = sorted((d, k) for d, k in deltas if d < 0)
    regressed = sorted(((d, k) for d, k in deltas if d > 0), reverse=True)
    for title, entries in (("좋아진 항목", improved), ("나빠진 항목", regressed)):
        lines += ["", f"## {title} ({len(entries)})", "", "| 항목 | 오류 수 변화 | 정답 | A | B |", "|---|---:|---|---|---|"]
        for delta, key in entries[:limit]:
            a, b = records_a[key], records_b[key]
            lines.append(f"| {key} | {delta:+d} | {_cell(a.item.text)} | {_cell(a.hypothesis)} | {_cell(b.hypothesis)} |")
    return "\n".join(lines) + "\n"
