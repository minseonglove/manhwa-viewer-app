from __future__ import annotations

import argparse
import json
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

from translation_eval import providers
from translation_eval.dataset import Page, latest_recognition_run, load_pages, page_image_data_url
from translation_eval.paths import GT_DIR, OCR_RUNS_DIR, PAGES_DIR, REPO_ROOT, RUNS_DIR
from translation_eval.prompt import PROMPTS, PROMPT_VERSIONS, build_messages, parse_output

PROVIDER_CHOICES = ("luna", "gemma_cf", "gemma_cf_think", "gemma31_fp4", "gemma31_fp8", "gemma31_bf16", *providers.OPENROUTER_MODELS)


def git_commit() -> str | None:
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=REPO_ROOT, text=True).strip()
    except Exception:  # noqa: BLE001
        return None


def new_run_dir(label: str, now: datetime | None = None) -> Path:
    stamp = (now or datetime.now()).strftime("%Y%m%d-%H%M%S")
    path = RUNS_DIR / f"{stamp}-{label}"
    path.mkdir(parents=True, exist_ok=False)
    return path


def run_provider(spec: providers.ProviderSpec, pages: list[Page], source: str, dry_run: bool, log, with_image: bool = False, prompt: str = "v1") -> dict:
    client = None if dry_run else providers.make_client(spec)
    out: dict[str, dict] = {}
    for page in pages:
        items = [item for item in page.items if item.text(source)]
        if not items:
            continue
        image = page_image_data_url(PAGES_DIR, page.name) if with_image else None
        messages = build_messages(items, source, image, prompt=prompt)
        expected = [item.id for item in items]
        if dry_run:
            fake = json.dumps({"items": [{"id": i.id, "ko": f"[dry] {i.text(source)}"} for i in items]}, ensure_ascii=False)
            result = providers.PageResult(content=fake, finish_reason="stop", settings={"dry_run": True})
            translations, problems = parse_output(result.content, expected)
        elif spec.segment_mode:
            from concurrent.futures import ThreadPoolExecutor
            with ThreadPoolExecutor(max_workers=6) as pool:
                seg = list(pool.map(lambda it: providers.translate_segment(client, spec, it.text(source)), items))
            translations = {it.id: r.content for it, r in zip(items, seg) if r.content}
            problems = [f"{it.id}: {r.error[:60]}" for it, r in zip(items, seg) if r.error]
            result = providers.PageResult(
                content=None, finish_reason="stop", latency_ms=max(r.latency_ms for r in seg),
                prompt_tokens=sum(r.prompt_tokens or 0 for r in seg), completion_tokens=sum(r.completion_tokens or 0 for r in seg),
                settings={"segment_mode": True, "served_by": seg[0].settings.get("served_by"), "billed_usd": sum(r.settings.get("billed_usd") or 0 for r in seg)},
            )
        else:
            result = providers.translate_page(client, spec, messages, PROMPTS[prompt][2])
            translations, problems = parse_output(result.content, expected) if not result.error else ({}, [])
        out[page.name] = {
            "items": {item.id: translations.get(item.id) for item in items},
            "problems": problems,
            "error": result.error,
            "refusal": result.refusal,
            "finish_reason": result.finish_reason,
            "latency_ms": result.latency_ms,
            "prompt_tokens": result.prompt_tokens,
            "completion_tokens": result.completion_tokens,
            "reasoning_tokens": result.reasoning_tokens,
            "neurons": result.neurons,
            "settings": result.settings,
        }
        status = result.error or (f"problems={len(problems)}" if problems else "ok")
        log(f"[{spec.name}] {page.name}: {len(items)} items, {result.latency_ms} ms, {status}")
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Translate the OCR eval pages with candidate models.")
    parser.add_argument("--source", choices=("gt", "ocr"), default="gt", help="gt: reviewed text, ocr: app OCR output")
    parser.add_argument("--providers", default="luna,gemma_cf", help="comma separated: " + ",".join(PROVIDER_CHOICES))
    parser.add_argument("--ocr-run", type=Path, default=None, help="eval/ocr run dir with recognition.json (default: latest)")
    parser.add_argument("--pages", default=None, help="comma separated page names, e.g. city01_p012,city01_p018")
    parser.add_argument("--limit", type=int, default=None, help="only the first N pages")
    parser.add_argument("--dry-run", action="store_true", help="no API calls; echoes the source text")
    parser.add_argument("--into", type=Path, default=None, help="add these providers to an existing run dir instead of creating a new one")
    parser.add_argument("--with-image", action="store_true", help="attach the page image to every request")
    parser.add_argument("--prompt", choices=tuple(PROMPTS), default="v2.1", help="prompt variant (v1 = original JSON prompt, v2 = compact, v2.1 = compact + sfx/background emphasis)")
    args = parser.parse_args(argv)

    names = [n.strip() for n in args.providers.split(",") if n.strip()]
    ocr_run = args.ocr_run or (latest_recognition_run(OCR_RUNS_DIR) if args.source == "ocr" else None)
    if args.source == "ocr" and ocr_run is None:
        print("no eval/ocr run with recognition.json found", file=sys.stderr)
        return 2
    existing_meta = json.loads((args.into / "meta.json").read_text(encoding="utf-8")) if args.into else None
    if existing_meta:
        if existing_meta["source"] != args.source:
            print(f"--into run has source {existing_meta['source']}, not {args.source}", file=sys.stderr)
            return 2
        if bool(existing_meta.get("with_image")) != args.with_image:
            print("--into run has a different --with-image setting", file=sys.stderr)
            return 2
        if existing_meta.get("prompt_version") != PROMPT_VERSIONS[args.prompt]:
            print(f"--into run used prompt {existing_meta.get('prompt_version')}, not {PROMPT_VERSIONS[args.prompt]}", file=sys.stderr)
            return 2
        ocr_run = Path(existing_meta["ocr_run"]) if existing_meta.get("ocr_run") else None
    page_filter = set(args.pages.split(",")) if args.pages else (set(existing_meta["pages"]) if existing_meta else None)
    pages = load_pages(GT_DIR, ocr_run=ocr_run, pages=page_filter)
    if args.limit:
        pages = pages[: args.limit]
    if not pages:
        print("no pages loaded", file=sys.stderr)
        return 2

    specs = (
        [providers.ProviderSpec(name=n, model=f"dry:{n}", base_url=None, api_key="dry") for n in names]
        if args.dry_run
        else providers.build_specs(names)
    )
    run_dir = args.into or new_run_dir(f"{args.source}" + (f"-{args.prompt}" if args.prompt != "v1" else "") + ("-img" if args.with_image else "") + ("-dry" if args.dry_run else ""))
    log = lambda msg: print(msg, flush=True)  # noqa: E731
    log(f"run dir: {run_dir}")
    log(f"pages: {len(pages)}, items: {sum(len([i for i in p.items if i.text(args.source)]) for p in pages)}")

    with ThreadPoolExecutor(max_workers=len(specs)) as pool:
        futures = {spec.name: pool.submit(run_provider, spec, pages, args.source, args.dry_run, log, args.with_image, args.prompt) for spec in specs}
        results = {name: fut.result() for name, fut in futures.items()}

    meta = existing_meta or {
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "source": args.source,
        "ocr_run": str(ocr_run) if ocr_run else None,
        "pages": [p.name for p in pages],
        "providers": {},
        "prompt_version": PROMPT_VERSIONS[args.prompt],
        "git_commit": git_commit(),
        "dry_run": args.dry_run,
        "with_image": args.with_image,
    }
    meta["providers"].update({spec.name: {"model": spec.model, "base_url": spec.base_url} for spec in specs})
    if existing_meta:
        results = {**json.loads((run_dir / "results.json").read_text(encoding="utf-8")), **results}
    (run_dir / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    (run_dir / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    log(f"wrote {run_dir / 'results.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
