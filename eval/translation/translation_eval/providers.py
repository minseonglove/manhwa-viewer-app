from __future__ import annotations

import os
import time
from dataclasses import dataclass, field
from pathlib import Path

from translation_eval.paths import LOCAL_PROPERTIES
from translation_eval.prompt import OUTPUT_SCHEMA

CLOUDFLARE_BASE_URL = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1"
OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
# name -> (provider order, quantizations). Pinned so that a run measures one serving precision.
OPENROUTER_ROUTES = {
    "gemma31_fp4": (["DeepInfra"], ["fp4"]),
    "gemma31_fp8": (["DeepInfra"], ["fp8"]),
    "gemma31_bf16": (["Crusoe", "Novita"], ["bf16"]),
}

# USD per 1M tokens (input, output). Used only for the report's cost estimate.
PRICES = {
    "gpt-5.6-luna": (0.20, 1.20),
    "@cf/google/gemma-4-26b-a4b-it": (0.10, 0.30),
    # OpenRouter routes: price depends on the pinned provider, so key by provider spec name.
    "gemma31_fp4": (0.09, 0.34),
    "gemma31_fp8": (0.13, 0.38),
    "gemma31_bf16": (0.14, 0.40),
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
            order, quantizations = OPENROUTER_ROUTES[name]
            specs.append(
                ProviderSpec(
                    name=name,
                    model="google/gemma-4-31b-it",
                    base_url=OPENROUTER_BASE_URL,
                    api_key=key,
                    extra_body={
                        "provider": {"order": order, "quantizations": quantizations, "allow_fallbacks": False, "data_collection": "deny"},
                        "reasoning": {"enabled": False},
                        "usage": {"include": True},
                    },
                )
            )
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
            billed_cost = extra.get("cost") if isinstance(extra, dict) else None
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
