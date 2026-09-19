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
