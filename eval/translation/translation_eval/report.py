from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

from translation_eval.dataset import load_pages
from translation_eval.paths import GT_DIR
from translation_eval.providers import PRICES_PER_MILLION_CHARS, price_for

LABELS = "ABCD"


def blind_key(pages: list[str], provider_names: list[str], seed: int) -> dict[str, dict[str, str]]:
    """Per page, map blind labels (A, B, ...) to provider names in a shuffled order."""
    rng = random.Random(seed)
    key: dict[str, dict[str, str]] = {}
    for page in pages:
        order = list(provider_names)
        rng.shuffle(order)
        key[page] = {LABELS[i]: name for i, name in enumerate(order)}
    return key


def _md(text: str | None) -> str:
    if text is None:
        return "*(없음)*"
    return text.replace("|", "\\|").replace("\n", " ")


def summarize(results: dict[str, dict], meta: dict) -> list[str]:
    lines = ["| 제공자 | 모델 | 페이지 | 항목 | 누락/형식 문제 | 오류 | 거절 | 평균 지연 ms | 입력 토큰 | 출력 토큰 | 추론 토큰 | 뉴런 | 추정 비용 USD |", "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for name, pages in results.items():
        model = meta["providers"][name]["model"]
        n_pages = len(pages)
        n_items = sum(len(p["items"]) for p in pages.values())
        n_missing = sum(sum(1 for v in p["items"].values() if v is None) for p in pages.values())
        n_errors = sum(1 for p in pages.values() if p["error"])
        n_refusals = sum(1 for p in pages.values() if p["refusal"] or p["finish_reason"] == "content_filter")
        latencies = [p["latency_ms"] for p in pages.values() if not p["error"]]
        avg_latency = round(sum(latencies) / len(latencies)) if latencies else 0
        p_tok = sum(p["prompt_tokens"] or 0 for p in pages.values())
        c_tok = sum(p["completion_tokens"] or 0 for p in pages.values())
        r_tok = sum(p["reasoning_tokens"] or 0 for p in pages.values())
        neurons = sum(p.get("neurons") or 0 for p in pages.values())
        neurons_s = f"{neurons:.0f}" if neurons else "-"
        price = price_for(model, name)
        char_price = PRICES_PER_MILLION_CHARS.get(name)
        if char_price is not None:
            chars = sum((p.get("settings") or {}).get("characters") or 0 for p in pages.values())
            cost = f"{chars * char_price / 1_000_000:.4f}"
        else:
            cost = f"{(p_tok * price[0] + c_tok * price[1]) / 1_000_000:.4f}" if price else "-"
        lines.append(
            f"| {name} | {model} | {n_pages} | {n_items} | {n_missing} | {n_errors} | {n_refusals} | {avg_latency} | {p_tok} | {c_tok} | {r_tok} | {neurons_s} | {cost} |"
        )
    return lines


def build_report(run_dir: Path, seed: int, unblind: bool = False) -> tuple[str, dict]:
    meta = json.loads((run_dir / "meta.json").read_text(encoding="utf-8"))
    results = json.loads((run_dir / "results.json").read_text(encoding="utf-8"))
    provider_names = list(results.keys())
    source = meta["source"]
    ocr_run = Path(meta["ocr_run"]) if meta.get("ocr_run") else None
    pages = load_pages(GT_DIR, ocr_run=ocr_run, pages=set(meta["pages"]))
    if unblind:
        key = {p.name: {name: name for name in provider_names} for p in pages}
    else:
        key = blind_key([p.name for p in pages], provider_names, seed)

    out = [f"# 번역 비교 ({'모델명 공개' if unblind else '블라인드'}) — {run_dir.name}", ""]
    out.append(f"- 입력: `{source}`" + (f" (OCR 실행 `{ocr_run.name}`)" if ocr_run else "") + (" + 페이지 이미지" if meta.get("with_image") else ""))
    out.append(f"- 프롬프트 버전: `{meta['prompt_version']}`, 커밋: `{meta.get('git_commit')}`")
    if unblind:
        out.append("- 열 제목이 모델 이름입니다.")
    else:
        out.append("- 열 A/B는 페이지마다 무작위로 섞였습니다. 매핑은 `key.json`, 공개는 `--reveal`.")
    out.append("")
    out.extend(summarize(results, meta))
    out.append("")

    for page in pages:
        labels = key[page.name]
        out.append(f"## {page.name}")
        notes = []
        for label, name in labels.items():
            entry = results[name].get(page.name)
            if entry is None:
                notes.append(f"{label}: 결과 없음")
            elif entry["error"]:
                notes.append(f"{label}: 오류 `{_md(entry['error'])[:120]}`")
            elif entry["problems"]:
                notes.append(f"{label}: {'; '.join(entry['problems'])}")
        if notes:
            out.append("> " + " / ".join(notes))
        header_cols = ["id", "종류", "입력(JA)"]
        if source == "ocr":
            header_cols.append("정답(JA)")
        header_cols.extend(labels.keys())
        out.append("| " + " | ".join(header_cols) + " |")
        out.append("|" + "---|" * len(header_cols))
        for item in page.items:
            text = item.text(source)
            if not text:
                continue
            row = [item.id, item.category, _md(text)]
            if source == "ocr":
                row.append("" if item.gt == text else _md(item.gt))
            for name in labels.values():
                entry = results[name].get(page.name) or {}
                row.append(_md((entry.get("items") or {}).get(item.id)))
            out.append("| " + " | ".join(row) + " |")
        out.append("")
    return "\n".join(out), key


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build a blind side-by-side report for a translation run.")
    parser.add_argument("run", type=Path)
    parser.add_argument("--seed", type=int, default=20260919)
    parser.add_argument("--reveal", action="store_true", help="print the label -> provider mapping per page")
    parser.add_argument("--unblind", action="store_true", help="write report-unblind.md with provider names as columns")
    args = parser.parse_args(argv)
    if args.reveal:
        key = json.loads((args.run / "key.json").read_text(encoding="utf-8"))
        for page, mapping in key.items():
            print(page, " ".join(f"{k}={v}" for k, v in mapping.items()))
        return 0
    report, key = build_report(args.run, args.seed, unblind=args.unblind)
    if args.unblind:
        (args.run / "report-unblind.md").write_text(report, encoding="utf-8")
        print(args.run / "report-unblind.md")
        return 0
    (args.run / "report.md").write_text(report, encoding="utf-8")
    (args.run / "key.json").write_text(json.dumps(key, ensure_ascii=False, indent=2), encoding="utf-8")
    print(args.run / "report.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
