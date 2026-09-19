from __future__ import annotations

import argparse
import json
import statistics
from pathlib import Path

from translation_eval.providers import price_for


def load(run: Path) -> tuple[dict, dict]:
    return (
        json.loads((run / "meta.json").read_text(encoding="utf-8")),
        json.loads((run / "results.json").read_text(encoding="utf-8")),
    )


def stats(pages: dict, model: str, name: str = "") -> dict:
    lat = [p["latency_ms"] for p in pages.values() if not p["error"]]
    p_tok = sum(p["prompt_tokens"] or 0 for p in pages.values())
    c_tok = sum(p["completion_tokens"] or 0 for p in pages.values())
    price = price_for(model, name)
    return {
        "pages": len(lat),
        "mean_ms": round(statistics.mean(lat)) if lat else 0,
        "median_ms": round(statistics.median(lat)) if lat else 0,
        "max_ms": max(lat) if lat else 0,
        "total_s": round(sum(lat) / 1000),
        "prompt_tokens": p_tok,
        "completion_tokens": c_tok,
        "neurons": round(sum(p.get("neurons") or 0 for p in pages.values())),
        "cost_usd": round((p_tok * price[0] + c_tok * price[1]) / 1_000_000, 4) if price else None,
    }


def changed_items(a: dict, b: dict) -> list[tuple[str, str, str, str]]:
    out = []
    for page, pa in a.items():
        pb = b.get(page)
        if not pb:
            continue
        for item, ko in pa["items"].items():
            kb = pb["items"].get(item)
            if ko != kb:
                out.append((page, item, ko or "", kb or ""))
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Compare two runs (e.g. text-only vs with image) per provider.")
    parser.add_argument("run_a", type=Path)
    parser.add_argument("run_b", type=Path)
    parser.add_argument("--show", type=int, default=0, help="print up to N changed items per provider")
    args = parser.parse_args(argv)
    meta_a, res_a = load(args.run_a)
    meta_b, res_b = load(args.run_b)
    print(f"A = {args.run_a.name} (image={meta_a.get('with_image', False)})")
    print(f"B = {args.run_b.name} (image={meta_b.get('with_image', False)})")
    print()
    print("| provider | run | pages | mean ms | median ms | max ms | total s | prompt tok | completion tok | neurons | cost USD |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for prov in res_a:
        if prov not in res_b:
            continue
        for label, res, meta in (("A", res_a, meta_a), ("B", res_b, meta_b)):
            s = stats(res[prov], meta["providers"][prov]["model"], prov)
            print(f"| {prov} | {label} | {s['pages']} | {s['mean_ms']} | {s['median_ms']} | {s['max_ms']} | {s['total_s']} | {s['prompt_tokens']} | {s['completion_tokens']} | {s['neurons']} | {s['cost_usd']} |")
    print()
    for prov in res_a:
        if prov not in res_b:
            continue
        changed = changed_items(res_a[prov], res_b[prov])
        total = sum(len(p["items"]) for p in res_a[prov].values())
        print(f"{prov}: {len(changed)}/{total} items differ between A and B")
        for page, item, ka, kb in changed[: args.show]:
            print(f"  {page} {item}: {ka!r} -> {kb!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
