from __future__ import annotations

import json
import os
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from translation_eval.paths import LOCAL_PROPERTIES
from translation_eval.prompt import OUTPUT_SCHEMA

CLOUDFLARE_BASE_URL = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1"
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
GOOGLE_NMT_URL = "https://translation.googleapis.com/language/translate/v2"
DEEPINFRA_BASE_URL = "https://api.deepinfra.com/v1/openai"
CLOUDFLARE_RUN_URL = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}"
# Generic OpenRouter models: name -> (model id, translation-only model that needs one call per segment).
OPENROUTER_MODELS = {
    "hymt2_30b": ("tencent/hy-mt2-30b-a3b", True),
    "hymt2_7b": ("tencent/hy-mt2-7b", True),
    "qwen3_235b": ("qwen/qwen3-235b-a22b-2507", False),
    "qwen37_flash": ("qwen/qwen3.7-flash", False),
    "deepseek_v4_flash": ("deepseek/deepseek-v4-flash", False),
    "solar_pro4": ("upstage/solar-pro4", False),
    "mistral_small32": ("mistralai/mistral-small-3.2-24b-instruct", False),
}
MT_TEMPLATE = "Translate the following segment into Korean, without additional explanation.\n\n{text}"

# name -> (model id, provider order, quantizations). Pinned so that a run measures one host and one serving precision;
# unpinned OpenRouter traffic is spread over every host of the model, each with its own price and latency.
OPENROUTER_ROUTES = {
    "gemma31_fp4": ("google/gemma-4-31b-it", ["DeepInfra"], ["fp4"]),
    "gemma31_fp8": ("google/gemma-4-31b-it", ["DeepInfra"], ["fp8"]),
    "gemma31_bf16": ("google/gemma-4-31b-it", ["Crusoe", "Novita"], ["bf16"]),
    "deepseek_v4_flash_deepinfra": ("deepseek/deepseek-v4-flash", ["DeepInfra"], ["fp8"]),
}

# USD per 1M tokens (input, output). Used only for the report's cost estimate.
PRICES = {
    "gpt-5.6-luna": (0.20, 1.20),
    "@cf/google/gemma-4-26b-a4b-it": (0.10, 0.30),
    # OpenRouter routes: price depends on the pinned provider, so key by provider spec name.
    "gemma31_fp4": (0.09, 0.34),
    "gemma31_fp8": (0.13, 0.38),
    "gemma31_bf16": (0.14, 0.40),
    "hymt2_30b": (0.074, 0.295),
    "hymt2_7b": (0.074, 0.295),
    "qwen3_235b": (0.087, 0.35),
    "qwen37_flash": (0.03, 0.13),
    "deepseek_v4_flash": (0.046, 0.091),
    "deepseek_v4_flash_deepinfra": (0.09, 0.18),
    "deepseek_v4_flash_direct": (0.09, 0.18),
    "gemma_deepinfra": (0.07, 0.34),
    "solar_pro4": (0.09, 0.36),
    "mistral_small32": (0.094, 0.25),
}


# USD per 1M source characters. NMT engines bill by character, not by token.
PRICES_PER_MILLION_CHARS = {
    "google_nmt": 20.0,
}


def price_for(model: str, provider_name: str) -> tuple[float, float] | None:
    return PRICES.get(provider_name) or PRICES.get(model)


@dataclass(frozen=True)
class ProviderSpec:
    name: str
    model: str
    base_url: str | None
    api_key: str
    reasoning_efforts: tuple[str | None, ...] = (None,)
    response_format_modes: tuple[str, ...] = ("json_schema", "json_object", "none")
    extra_body: dict = field(default_factory=dict)
    max_completion_tokens: int | None = None
    segment_mode: bool = False
    kind: str = "chat"


@dataclass
class PageResult:
    content: str | None = None
    finish_reason: str | None = None
    refusal: str | None = None
    error: str | None = None
    latency_ms: int = 0
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    reasoning_tokens: int | None = None
    neurons: float | None = None
    settings: dict = field(default_factory=dict)


def read_local_properties(path: Path = LOCAL_PROPERTIES) -> dict[str, str]:
    if not path.exists():
        return {}
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def config_value(env_name: str, prop_name: str, props: dict[str, str]) -> str | None:
    return os.environ.get(env_name) or props.get(prop_name) or None


def build_specs(names: list[str], props: dict[str, str] | None = None) -> list[ProviderSpec]:
    props = read_local_properties() if props is None else props
    specs: list[ProviderSpec] = []
    for name in names:
        if name == "luna":
            key = config_value("OPENAI_API_KEY", "openai.api.key", props)
            if not key:
                raise SystemExit("luna: OPENAI_API_KEY or openai.api.key in local.properties is required")
            specs.append(
                ProviderSpec(
                    name=name,
                    model="gpt-5.6-luna",
                    base_url=None,
                    api_key=key,
                    reasoning_efforts=("none", "minimal", None),
                )
            )
        elif name in ("gemma_cf", "gemma_cf_think"):
            account = config_value("CLOUDFLARE_ACCOUNT_ID", "cloudflare.account.id", props)
            token = config_value("CLOUDFLARE_API_TOKEN", "cloudflare.api.token", props)
            if not (account and token):
                raise SystemExit(
                    f"{name}: CLOUDFLARE_ACCOUNT_ID/CLOUDFLARE_API_TOKEN or cloudflare.account.id/"
                    "cloudflare.api.token in local.properties are required"
                )
            think = name == "gemma_cf_think"
            specs.append(
                ProviderSpec(
                    name=name,
                    model="@cf/google/gemma-4-26b-a4b-it",
                    base_url=CLOUDFLARE_BASE_URL.format(account_id=account),
                    api_key=token,
                    # Gemma 4 thinks by default and bills the thinking as output tokens;
                    # only the chat-template switch controls it on Workers AI.
                    extra_body={"chat_template_kwargs": {"enable_thinking": think}},
                    max_completion_tokens=4096 if think else None,
                )
            )
        elif name in OPENROUTER_ROUTES:
            key = config_value("OPENROUTER_API_KEY", "openrouter.api.key", props)
            if not key:
                raise SystemExit(f"{name}: OPENROUTER_API_KEY or openrouter.api.key in local.properties is required")
            model, order, quantizations = OPENROUTER_ROUTES[name]
            specs.append(
                ProviderSpec(
                    name=name,
                    model=model,
                    base_url=OPENROUTER_BASE_URL,
                    api_key=key,
                    extra_body={
                        "provider": {"order": order, "quantizations": quantizations, "allow_fallbacks": False, "data_collection": "deny"},
                        "reasoning": {"enabled": False},
                        "usage": {"include": True},
                    },
                )
            )
        elif name in OPENROUTER_MODELS:
            key = config_value("OPENROUTER_API_KEY", "openrouter.api.key", props)
            if not key:
                raise SystemExit(f"{name}: OPENROUTER_API_KEY or openrouter.api.key in local.properties is required")
            model, segment_mode = OPENROUTER_MODELS[name]
            specs.append(
                ProviderSpec(
                    name=name,
                    model=model,
                    base_url=OPENROUTER_BASE_URL,
                    api_key=key,
                    extra_body={
                        "provider": {"data_collection": "deny"},
                        "reasoning": {"enabled": False},
                        "usage": {"include": True},
                    },
                    segment_mode=segment_mode,
                    response_format_modes=("none",) if segment_mode else ("json_object", "none"),
                )
            )
        elif name == "deepseek_v4_flash_direct":
            key = config_value("DEEPINFRA_API_KEY", "deepinfra.api.key", props)
            if not key:
                raise SystemExit("deepseek_v4_flash_direct: DEEPINFRA_API_KEY or deepinfra.api.key in local.properties is required")
            specs.append(
                ProviderSpec(
                    name=name,
                    model="deepseek-ai/DeepSeek-V4-Flash",
                    base_url=DEEPINFRA_BASE_URL,
                    api_key=key,
                    response_format_modes=("json_object", "none"),
                )
            )
        elif name == "gemma_deepinfra":
            key = config_value("DEEPINFRA_API_KEY", "deepinfra.api.key", props)
            if not key:
                raise SystemExit("gemma_deepinfra: DEEPINFRA_API_KEY or deepinfra.api.key in local.properties is required")
            specs.append(
                ProviderSpec(
                    name=name,
                    model="google/gemma-4-26B-A4B-it",
                    base_url=DEEPINFRA_BASE_URL,
                    api_key=key,
                    # DeepInfra serves Gemma 4 with thinking off by default (measured: same 290 output tokens with or without
                    # the switch); it is set anyway so a change of the host default cannot silently multiply the output.
                    extra_body={"chat_template_kwargs": {"enable_thinking": False}},
                    response_format_modes=("json_object", "none"),
                )
            )
        elif name == "m2m100_cf":
            account = config_value("CLOUDFLARE_ACCOUNT_ID", "cloudflare.account.id", props)
            token = config_value("CLOUDFLARE_API_TOKEN", "cloudflare.api.token", props)
            if not (account and token):
                raise SystemExit("m2m100_cf: CLOUDFLARE_ACCOUNT_ID/CLOUDFLARE_API_TOKEN or cloudflare.account.id/cloudflare.api.token in local.properties are required")
            model = "@cf/meta/m2m100-1.2b"
            specs.append(ProviderSpec(name=name, model=model, base_url=CLOUDFLARE_RUN_URL.format(account_id=account, model=model), api_key=token, kind="m2m100"))
        elif name == "proxy":
            url = config_value("PROXY_URL", "proxy.url", props)
            secret = config_value("PROXY_DEV_SECRET", "proxy.dev.secret", props)
            if not (url and secret):
                raise SystemExit("proxy: PROXY_URL/PROXY_DEV_SECRET or proxy.url/proxy.dev.secret in local.properties are required")
            specs.append(ProviderSpec(name=name, model="translation-proxy", base_url=url.rstrip("/"), api_key=secret, kind="proxy"))
        elif name == "google_nmt":
            key = config_value("GOOGLE_TRANSLATE_API_KEY", "google.translate.api.key", props)
            if not key:
                raise SystemExit("google_nmt: GOOGLE_TRANSLATE_API_KEY or google.translate.api.key in local.properties is required")
            specs.append(ProviderSpec(name=name, model="google-nmt-v2", base_url=GOOGLE_NMT_URL, api_key=key, kind="nmt"))
        else:
            raise SystemExit(f"unknown provider: {name}")
    return specs


def _response_format(mode: str) -> dict | None:
    if mode == "json_schema":
        return {
            "type": "json_schema",
            "json_schema": {"name": "translations", "strict": True, "schema": OUTPUT_SCHEMA},
        }
    if mode == "json_object":
        return {"type": "json_object"}
    return None


def make_client(spec: ProviderSpec):
    from openai import OpenAI

    return OpenAI(api_key=spec.api_key, base_url=spec.base_url, max_retries=3, timeout=180.0)


def _is_bad_request(exc: Exception) -> bool:
    from openai import BadRequestError

    return isinstance(exc, BadRequestError)


def translate_page(client, spec: ProviderSpec, messages: list[dict], response_format_modes: tuple[str, ...] | None = None) -> PageResult:
    """Call the model once, degrading response_format/reasoning settings on 400s.

    The degradation order is fixed so that every page of one run is served with the
    same settings once the first page has found what the endpoint accepts.
    """
    last_error: str | None = None
    modes = response_format_modes or spec.response_format_modes
    for effort in spec.reasoning_efforts:
        for mode in modes:
            kwargs: dict = {"model": spec.model, "messages": messages, "temperature": 0.2}
            if spec.extra_body:
                kwargs["extra_body"] = spec.extra_body
            if spec.max_completion_tokens:
                kwargs["max_completion_tokens"] = spec.max_completion_tokens
            fmt = _response_format(mode)
            if fmt is not None:
                kwargs["response_format"] = fmt
            if effort is not None:
                kwargs["reasoning_effort"] = effort
            started = time.perf_counter()
            try:
                response = client.chat.completions.create(**kwargs)
            except Exception as exc:  # noqa: BLE001 - recorded, not swallowed
                last_error = f"{type(exc).__name__}: {exc}"
                if _is_bad_request(exc):
                    continue
                return PageResult(error=last_error, latency_ms=int((time.perf_counter() - started) * 1000))
            latency_ms = int((time.perf_counter() - started) * 1000)
            choice = response.choices[0] if response.choices else None
            message = choice.message if choice else None
            usage = response.usage
            details = getattr(usage, "completion_tokens_details", None) if usage else None
            extra = getattr(usage, "model_extra", None) or {}
            served_by = (getattr(response, "model_extra", None) or {}).get("provider")
            billed_cost = (extra.get("cost") if extra.get("cost") is not None else extra.get("estimated_cost")) if isinstance(extra, dict) else None
            return PageResult(
                content=message.content if message else None,
                finish_reason=choice.finish_reason if choice else None,
                refusal=getattr(message, "refusal", None) if message else None,
                latency_ms=latency_ms,
                prompt_tokens=usage.prompt_tokens if usage else None,
                completion_tokens=usage.completion_tokens if usage else None,
                reasoning_tokens=getattr(details, "reasoning_tokens", None) if details else None,
                neurons=extra.get("neurons") if isinstance(extra, dict) else None,
                settings={"response_format": mode, "reasoning_effort": effort, **({"served_by": served_by} if served_by else {}), **({"billed_usd": billed_cost} if billed_cost is not None else {})},
            )
    return PageResult(error=last_error or "all settings rejected")


def translate_segment(client, spec: ProviderSpec, text: str) -> PageResult:
    """One call for one segment with the MT prompt template (translation-only models)."""
    started = time.perf_counter()
    kwargs: dict = {"model": spec.model, "messages": [{"role": "user", "content": MT_TEMPLATE.format(text=text)}], "temperature": 0.2}
    if spec.extra_body:
        kwargs["extra_body"] = spec.extra_body
    try:
        response = client.chat.completions.create(**kwargs)
    except Exception as exc:  # noqa: BLE001
        return PageResult(error=f"{type(exc).__name__}: {exc}", latency_ms=int((time.perf_counter() - started) * 1000))
    choice = response.choices[0] if response.choices else None
    message = choice.message if choice else None
    usage = response.usage
    extra = getattr(usage, "model_extra", None) or {}
    return PageResult(
        content=(message.content or "").strip() if message else None,
        finish_reason=choice.finish_reason if choice else None,
        refusal=getattr(message, "refusal", None) if message else None,
        latency_ms=int((time.perf_counter() - started) * 1000),
        prompt_tokens=usage.prompt_tokens if usage else None,
        completion_tokens=usage.completion_tokens if usage else None,
        neurons=None,
        settings={"segment_mode": True, "served_by": (getattr(response, "model_extra", None) or {}).get("provider"), "billed_usd": extra.get("cost") if isinstance(extra, dict) else None},
    )


# Cloudflare answers urllib's default "Python-urllib/x.y" signature with 403 (error code 1010) on workers.dev.
_USER_AGENT = "manhwa-viewer-translation-eval/1.0"


def _http_post_json(url: str, headers: dict[str, str], body: dict) -> tuple[int, str]:
    request = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"User-Agent": _USER_AGENT, **headers, "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")


def translate_nmt(spec: ProviderSpec, texts: list[str], source: str = "ja", target: str = "ko", post=_http_post_json) -> tuple[list[str], PageResult]:
    """One call for all segments of a page. Each text is its own `q` entry, so the answer stays aligned by index."""
    started = time.perf_counter()
    settings = {"nmt": True, "characters": sum(len(t) for t in texts)}
    # format=text: the default (html) returns HTML-escaped entities. The key goes in a header so it never lands in a URL.
    status, payload = post(spec.base_url, {"x-goog-api-key": spec.api_key}, {"q": texts, "source": source, "target": target, "format": "text"})
    latency_ms = int((time.perf_counter() - started) * 1000)
    if not 200 <= status < 300:
        return [], PageResult(error=f"HTTP {status}: {payload[:200]}", latency_ms=latency_ms, settings=settings)
    translated = [entry["translatedText"] for entry in json.loads(payload)["data"]["translations"]]
    if len(translated) != len(texts):
        return [], PageResult(error=f"{len(translated)} translations for {len(texts)} texts", latency_ms=latency_ms, settings=settings)
    return translated, PageResult(finish_reason="stop", latency_ms=latency_ms, settings=settings)


def translate_proxy(spec: ProviderSpec, items: list[dict], session: dict, glossary: list[dict] | None = None, post=_http_post_json) -> tuple[dict[str, str], PageResult]:
    """One page through the translation proxy. `session` caches the token so a run opens one session, as the app would."""
    started = time.perf_counter()
    if "token" not in session:
        status, payload = post(f"{spec.base_url}/v1/session", {}, {"platform": "dev", "deviceId": "translation-eval", "secret": spec.api_key})
        if not 200 <= status < 300:
            return {}, PageResult(error=f"session HTTP {status}: {payload[:200]}", latency_ms=int((time.perf_counter() - started) * 1000))
        session.update(json.loads(payload))
    status, payload = post(f"{spec.base_url}/v1/translate", {"authorization": f"Bearer {session['token']}"}, {"items": items, "source": "ja", "target": "ko", "glossary": glossary or []})
    latency_ms = int((time.perf_counter() - started) * 1000)
    data = json.loads(payload) if payload.lstrip().startswith("{") else {}
    if not 200 <= status < 300:
        reasons = "; ".join(f"{a.get('engine')}={a.get('reason')}" for a in data.get("attempts", []))
        return {}, PageResult(error=f"HTTP {status}: {reasons or data.get('error') or payload[:200]}", latency_ms=latency_ms, settings={"proxy": True})
    return data["translations"], PageResult(finish_reason="stop", latency_ms=latency_ms, settings={"proxy": True, "engine": data.get("engine"), "tier": session.get("tier")})


def translate_m2m100(spec: ProviderSpec, texts: list[str], source: str = "ja", target: str = "ko", post=_http_post_json, workers: int = 6) -> tuple[list[str | None], PageResult]:
    """Sentence-level NMT on Workers AI: one request per segment. A failed segment stays None so the others keep their place."""
    from concurrent.futures import ThreadPoolExecutor

    started = time.perf_counter()

    def one(text: str) -> tuple[str | None, str | None]:
        status, payload = post(spec.base_url, {"Authorization": f"Bearer {spec.api_key}"}, {"text": text, "source_lang": source, "target_lang": target})
        if not 200 <= status < 300:
            return None, f"HTTP {status}: {payload[:120]}"
        translated = (json.loads(payload).get("result") or {}).get("translated_text")
        return (translated, None) if isinstance(translated, str) else (None, f"no translated_text: {payload[:120]}")

    with ThreadPoolExecutor(max_workers=workers) as pool:
        outcomes = list(pool.map(one, texts))
    errors = [e for _, e in outcomes if e]
    settings = {"m2m100": True, "characters": sum(len(t) for t in texts), "failed_segments": len(errors), **({"first_error": errors[0]} if errors else {})}
    return [t for t, _ in outcomes], PageResult(finish_reason="stop", latency_ms=int((time.perf_counter() - started) * 1000), settings=settings)
