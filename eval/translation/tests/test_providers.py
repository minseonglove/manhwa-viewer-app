import json

import pytest

from translation_eval import providers


def test_read_local_properties_parses_key_values(tmp_path):
    path = tmp_path / "local.properties"
    path.write_text("# comment\nsdk.dir=/x\nopenai.api.key = sk-test \n\nbroken line\n", encoding="utf-8")
    assert providers.read_local_properties(path) == {"sdk.dir": "/x", "openai.api.key": "sk-test"}


def test_build_specs_reads_props_and_env(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("CLOUDFLARE_API_TOKEN", "env-token")
    props = {"openai.api.key": "sk-prop", "cloudflare.account.id": "acc123", "cloudflare.api.token": "prop-token"}
    specs = {s.name: s for s in providers.build_specs(["luna", "gemma_cf"], props)}
    assert specs["luna"].model == "gpt-5.6-luna" and specs["luna"].api_key == "sk-prop"
    assert specs["gemma_cf"].api_key == "env-token"
    assert specs["gemma_cf"].base_url.endswith("/accounts/acc123/ai/v1")
    assert specs["gemma_cf"].extra_body == {"chat_template_kwargs": {"enable_thinking": False}}


def test_build_specs_fails_without_credentials(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(SystemExit):
        providers.build_specs(["luna"], {})


class _FakeCompletions:
    def __init__(self, reject_modes):
        self.reject_modes = reject_modes
        self.calls = []

    def create(self, **kwargs):
        from openai import BadRequestError
        import httpx2 as httpx

        self.calls.append(kwargs)
        mode = (kwargs.get("response_format") or {}).get("type", "none")
        if mode in self.reject_modes or kwargs.get("reasoning_effort") == "none":
            resp = httpx.Response(400, request=httpx.Request("POST", "http://x"))
            raise BadRequestError("bad", response=resp, body=None)
        return _FakeResponse()


class _FakeResponse:
    class _Usage:
        prompt_tokens = 10
        completion_tokens = 5
        completion_tokens_details = None

    class _Choice:
        finish_reason = "stop"

        class message:
            content = '{"items": []}'
            refusal = None

    choices = [_Choice()]
    usage = _Usage()


class _FakeClient:
    def __init__(self, reject_modes):
        self.chat = type("Chat", (), {})()
        self.chat.completions = _FakeCompletions(reject_modes)


def test_translate_page_degrades_settings_on_400():
    spec = providers.ProviderSpec(name="x", model="m", base_url=None, api_key="k", reasoning_efforts=("none", "minimal"), extra_body={"k": 1})
    client = _FakeClient(reject_modes={"json_schema"})
    result = providers.translate_page(client, spec, [{"role": "user", "content": "hi"}])
    assert result.error is None
    assert client.chat.completions.calls[-1]["extra_body"] == {"k": 1}
    assert result.settings == {"response_format": "json_object", "reasoning_effort": "minimal"}
    assert result.prompt_tokens == 10 and result.completion_tokens == 5


def test_think_variant_enables_thinking_and_caps_output():
    props = {"cloudflare.account.id": "acc", "cloudflare.api.token": "tok"}
    spec = providers.build_specs(["gemma_cf_think"], props)[0]
    assert spec.extra_body == {"chat_template_kwargs": {"enable_thinking": True}}
    assert spec.max_completion_tokens == 4096 and spec.model == "@cf/google/gemma-4-26b-a4b-it"


def test_openrouter_specs_pin_provider_precision_and_deny_data_collection():
    props = {"openrouter.api.key": "sk-or-test"}
    specs = {s.name: s for s in providers.build_specs(["gemma31_fp4", "gemma31_bf16"], props)}
    assert specs["gemma31_fp4"].extra_body["provider"] == {"order": ["DeepInfra"], "quantizations": ["fp4"], "allow_fallbacks": False, "data_collection": "deny"}
    assert specs["gemma31_bf16"].extra_body["provider"]["order"] == ["Crusoe", "Novita"]
    assert specs["gemma31_fp4"].model == "google/gemma-4-31b-it" and specs["gemma31_fp4"].base_url == providers.OPENROUTER_BASE_URL
    assert providers.price_for("google/gemma-4-31b-it", "gemma31_bf16") == (0.14, 0.40)


def _nmt_spec():
    return providers.build_specs(["google_nmt"], {"google.translate.api.key": "gk"})[0]


def test_build_specs_google_nmt_requires_key_and_marks_nmt_kind(monkeypatch):
    monkeypatch.delenv("GOOGLE_TRANSLATE_API_KEY", raising=False)
    spec = _nmt_spec()
    assert spec.kind == "nmt" and spec.api_key == "gk"
    assert spec.base_url == "https://translation.googleapis.com/language/translate/v2"
    with pytest.raises(SystemExit):
        providers.build_specs(["google_nmt"], {})


def test_translate_nmt_sends_texts_as_array_with_key_in_header():
    calls = []

    def fake_post(url, headers, body):
        calls.append((url, headers, body))
        return 200, json.dumps({"data": {"translations": [{"translatedText": "가"}, {"translatedText": "나"}]}})

    translated, result = providers.translate_nmt(_nmt_spec(), ["一行目\n二行目", "b"], post=fake_post)

    assert translated == ["가", "나"] and result.error is None
    url, headers, body = calls[0]
    assert url == "https://translation.googleapis.com/language/translate/v2"
    assert headers["x-goog-api-key"] == "gk"
    assert body == {"q": ["一行目\n二行目", "b"], "source": "ja", "target": "ko", "format": "text"}
    assert result.settings == {"nmt": True, "characters": 8}


def test_translate_nmt_reports_count_mismatch_instead_of_misaligned_items():
    def fake_post(url, headers, body):
        return 200, json.dumps({"data": {"translations": [{"translatedText": "하나뿐"}]}})

    translated, result = providers.translate_nmt(_nmt_spec(), ["a", "b"], post=fake_post)

    assert translated == [] and "1 translations for 2 texts" in result.error


def test_translate_nmt_reports_http_error():
    def fake_post(url, headers, body):
        return 403, '{"error": {"code": 403, "status": "PERMISSION_DENIED"}}'

    translated, result = providers.translate_nmt(_nmt_spec(), ["a"], post=fake_post)

    assert translated == [] and result.error.startswith("HTTP 403")


def test_pinned_deepseek_route_serves_from_one_provider_without_fallback():
    spec = providers.build_specs(["deepseek_v4_flash_deepinfra"], {"openrouter.api.key": "sk-or-test"})[0]
    assert spec.model == "deepseek/deepseek-v4-flash" and spec.base_url == providers.OPENROUTER_BASE_URL
    assert spec.extra_body["provider"] == {"order": ["DeepInfra"], "quantizations": ["fp8"], "allow_fallbacks": False, "data_collection": "deny"}
    assert providers.price_for(spec.model, spec.name) == (0.09, 0.18)


def test_deepinfra_direct_spec_calls_deepinfra_without_openrouter(monkeypatch):
    monkeypatch.delenv("DEEPINFRA_API_KEY", raising=False)
    spec = providers.build_specs(["deepseek_v4_flash_direct"], {"deepinfra.api.key": "di-key"})[0]
    assert spec.base_url == "https://api.deepinfra.com/v1/openai" and spec.model == "deepseek-ai/DeepSeek-V4-Flash"
    assert spec.api_key == "di-key" and "provider" not in spec.extra_body
    assert providers.price_for(spec.model, spec.name) == (0.09, 0.18)
    with pytest.raises(SystemExit):
        providers.build_specs(["deepseek_v4_flash_direct"], {})


def test_translate_page_records_deepinfra_estimated_cost_as_billed():
    class _Usage(_FakeResponse._Usage):
        model_extra = {"estimated_cost": 0.00012}

    class _Response(_FakeResponse):
        usage = _Usage()

    class _Completions:
        def create(self, **kwargs):
            return _Response()

    client = type("Client", (), {"chat": type("Chat", (), {"completions": _Completions()})()})()
    spec = providers.ProviderSpec(name="x", model="m", base_url=None, api_key="k")
    result = providers.translate_page(client, spec, [{"role": "user", "content": "hi"}])
    assert result.settings["billed_usd"] == 0.00012


def _proxy_spec():
    return providers.build_specs(["proxy"], {"proxy.url": "http://proxy.test", "proxy.dev.secret": "s3cret"})[0]


def test_proxy_spec_requires_url_and_dev_secret(monkeypatch):
    monkeypatch.delenv("PROXY_URL", raising=False); monkeypatch.delenv("PROXY_DEV_SECRET", raising=False)
    spec = _proxy_spec()
    assert spec.kind == "proxy" and spec.base_url == "http://proxy.test" and spec.api_key == "s3cret"
    with pytest.raises(SystemExit):
        providers.build_specs(["proxy"], {"proxy.url": "http://proxy.test"})


def test_translate_proxy_opens_one_session_and_reports_the_engine_that_answered():
    calls = []

    def fake_post(url, headers, body):
        calls.append((url, headers, body))
        if url.endswith("/v1/session"):
            return 200, json.dumps({"token": "tok", "tier": "attested"})
        return 200, json.dumps({"translations": {"t01": "가", "t02": "나"}, "engine": "gemma"})

    spec, session = _proxy_spec(), {}
    items = [{"id": "t01", "kind": "bubble", "text": "あ"}, {"id": "t02", "kind": "sfx", "text": "ドン"}]
    first, r1 = providers.translate_proxy(spec, items, session=session, post=fake_post)
    second, r2 = providers.translate_proxy(spec, items, session=session, post=fake_post)

    assert first == {"t01": "가", "t02": "나"} and r1.error is None and r1.settings["engine"] == "gemma"
    assert [c[0] for c in calls] == ["http://proxy.test/v1/session", "http://proxy.test/v1/translate", "http://proxy.test/v1/translate"]
    assert calls[0][2] == {"platform": "dev", "deviceId": "translation-eval", "secret": "s3cret"}
    assert calls[1][1]["authorization"] == "Bearer tok"
    assert calls[1][2] == {"items": items, "source": "ja", "target": "ko", "glossary": []}


def test_translate_proxy_records_why_every_engine_failed():
    def fake_post(url, headers, body):
        if url.endswith("/v1/session"):
            return 200, json.dumps({"token": "tok", "tier": "attested"})
        return 502, json.dumps({"error": "all translation engines failed", "attempts": [{"engine": "gemma", "ok": False, "reason": "HTTP 500"}, {"engine": "google", "ok": False, "reason": "timeout after 3000ms"}]})

    translated, result = providers.translate_proxy(_proxy_spec(), [{"id": "t01", "kind": "bubble", "text": "あ"}], session={}, post=fake_post)
    assert translated == {} and result.error == "HTTP 502: gemma=HTTP 500; google=timeout after 3000ms"


def _m2m_spec():
    return providers.build_specs(["m2m100_cf"], {"cloudflare.account.id": "acc123", "cloudflare.api.token": "cf-token"})[0]


def test_m2m100_spec_points_at_the_workers_ai_run_endpoint(monkeypatch):
    monkeypatch.delenv("CLOUDFLARE_ACCOUNT_ID", raising=False); monkeypatch.delenv("CLOUDFLARE_API_TOKEN", raising=False)
    spec = _m2m_spec()
    assert spec.kind == "m2m100" and spec.api_key == "cf-token"
    assert spec.base_url == "https://api.cloudflare.com/client/v4/accounts/acc123/ai/run/@cf/meta/m2m100-1.2b"
    with pytest.raises(SystemExit):
        providers.build_specs(["m2m100_cf"], {})


def test_translate_m2m100_sends_one_request_per_text_and_keeps_order():
    calls = []

    def fake_post(url, headers, body):
        calls.append((url, headers, body))
        return 200, json.dumps({"success": True, "result": {"translated_text": "번역:" + body["text"]}})

    translated, result = providers.translate_m2m100(_m2m_spec(), ["あ", "い"], post=fake_post, workers=1)

    assert translated == ["번역:あ", "번역:い"] and result.error is None
    assert [c[2] for c in calls] == [{"text": "あ", "source_lang": "ja", "target_lang": "ko"}, {"text": "い", "source_lang": "ja", "target_lang": "ko"}]
    assert calls[0][1]["Authorization"] == "Bearer cf-token"
    assert result.settings["characters"] == 2


def test_translate_m2m100_reports_a_failed_segment_without_shifting_the_others():
    def fake_post(url, headers, body):
        if body["text"] == "い":
            return 500, '{"success": false}'
        return 200, json.dumps({"success": True, "result": {"translated_text": "번역:" + body["text"]}})

    translated, result = providers.translate_m2m100(_m2m_spec(), ["あ", "い", "う"], post=fake_post, workers=1)

    assert translated == ["번역:あ", None, "번역:う"]
    assert result.settings["failed_segments"] == 1


def test_gemma_deepinfra_spec_pins_thinking_off_on_the_second_host(monkeypatch):
    monkeypatch.delenv("DEEPINFRA_API_KEY", raising=False)
    spec = providers.build_specs(["gemma_deepinfra"], {"deepinfra.api.key": "di-key"})[0]
    assert spec.base_url == providers.DEEPINFRA_BASE_URL and spec.model == "google/gemma-4-26B-A4B-it" and spec.api_key == "di-key"
    assert spec.extra_body == {"chat_template_kwargs": {"enable_thinking": False}}
    assert providers.price_for(spec.model, spec.name) == (0.07, 0.34)
    with pytest.raises(SystemExit):
        providers.build_specs(["gemma_deepinfra"], {})
