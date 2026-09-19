import { describe, expect, it } from "vitest";
import { deepseekEngine, gemmaEngine, googleEngine } from "../src/engines";

const items = [{ id: "t01", kind: "bubble" as const, text: "南雲？" }, { id: "t02", kind: "sfx" as const, text: "ドン" }];
const glossary = [{ src: "南雲", ko: "나구모" }];
const never = new AbortController().signal;

function fakeFetch(respond: (url: string, init: RequestInit) => Response) {
  const calls: Array<{ url: string; init: RequestInit; body: any }> = [];
  const fn = (async (url: string, init: RequestInit) => {
    calls.push({ url, init, body: JSON.parse(init.body as string) });
    return respond(url, init);
  }) as unknown as typeof fetch;
  return { fn, calls };
}
const chatReply = (content: string, extra: object = {}) =>
  new Response(JSON.stringify({ choices: [{ finish_reason: "stop", message: { content }, ...extra }] }), { status: 200 });

function fakeAi(reply: unknown) {
  const calls: Array<{ model: string; input: any }> = [];
  return { calls, ai: { run: async (model: string, input: unknown) => { calls.push({ model, input }); return reply; } } };
}
const aiReply = (content: string | null, extra: object = {}) => ({ choices: [{ finish_reason: "stop", message: { content, ...extra } }] });

describe("gemmaEngine", () => {
  it("runs Gemma through the Workers AI binding with thinking switched off", async () => {
    const { ai, calls } = fakeAi(aiReply('{"t01":"나구모?","t02":"쾅"}'));
    const out = await gemmaEngine(ai).translate(items, glossary, never);
    expect(out).toEqual({ t01: "나구모?", t02: "쾅" });
    const { model, input } = calls[0]!;
    expect(model).toBe("@cf/google/gemma-4-26b-a4b-it");
    expect(input.temperature).toBe(0.2);
    expect(input.response_format).toEqual({ type: "json_object" });
    expect(input.chat_template_kwargs).toEqual({ enable_thinking: false });
    expect(input.messages[0].content.endsWith("南雲=나구모")).toBe(true);
    expect(input.messages[1].content).toBe("t01\tbubble\t南雲？\nt02\tsfx\tドン");
  });
  it("fails on a refusal and when the answer does not cover every id", async () => {
    await expect(gemmaEngine(fakeAi(aiReply(null, { refusal: "no" })).ai).translate(items, [], never)).rejects.toThrow("refused");
    await expect(gemmaEngine(fakeAi(aiReply('{"t01":"a"}')).ai).translate(items, [], never)).rejects.toThrow("missing ids: t02");
  });
});

describe("deepseekEngine", () => {
  it("talks to DeepInfra directly", async () => {
    const { fn, calls } = fakeFetch(() => chatReply('{"t01":"a","t02":"b"}'));
    await deepseekEngine({ apiKey: "di-key" }, fn).translate(items, [], never);
    expect(calls[0]!.url).toBe("https://api.deepinfra.com/v1/openai/chat/completions");
    expect(calls[0]!.body.model).toBe("deepseek-ai/DeepSeek-V4-Flash");
    expect(calls[0]!.body.chat_template_kwargs).toBeUndefined();
    expect((calls[0]!.init.headers as Record<string, string>).authorization).toBe("Bearer di-key");
  });
  it("fails on an HTTP error without repeating the provider's response body", async () => {
    const { fn } = fakeFetch(() => new Response("upstream echoed: 南雲？", { status: 500 }));
    await expect(deepseekEngine({ apiKey: "k" }, fn).translate(items, [], never)).rejects.toThrow(/^HTTP 500$/);
  });
  it("fails on a refusal and on a content filter stop", async () => {
    const refusal = fakeFetch(() => new Response(JSON.stringify({ choices: [{ finish_reason: "stop", message: { content: null, refusal: "no" } }] })));
    await expect(deepseekEngine({ apiKey: "k" }, refusal.fn).translate(items, [], never)).rejects.toThrow("refused");
    const filtered = fakeFetch(() => new Response(JSON.stringify({ choices: [{ finish_reason: "content_filter", message: { content: "" } }] })));
    await expect(deepseekEngine({ apiKey: "k" }, filtered.fn).translate(items, [], never)).rejects.toThrow("refused");
  });
  it("reports unparseable output without quoting it", async () => {
    const { fn } = fakeFetch(() => chatReply("南雲？ 는 번역할 수 없습니다"));
    const error = await deepseekEngine({ apiKey: "k" }, fn).translate(items, [], never).catch((e: Error) => e);
    expect((error as Error).message).toBe("invalid json");
  });
  it("fails when the answer does not cover every id", async () => {
    const { fn } = fakeFetch(() => chatReply('{"t01":"a"}'));
    await expect(deepseekEngine({ apiKey: "k" }, fn).translate(items, [], never)).rejects.toThrow("missing ids: t02");
  });
});

describe("googleEngine", () => {
  it("sends each text as its own entry, the key in a header, and names already in Hangul", async () => {
    const { fn, calls } = fakeFetch(() => new Response(JSON.stringify({ data: { translations: [{ translatedText: "나구모?" }, { translatedText: "쾅" }] } })));
    const out = await googleEngine({ apiKey: "g-key" }, fn).translate(items, glossary, never);
    expect(out).toEqual({ t01: "나구모?", t02: "쾅" });
    const { url, init, body } = calls[0]!;
    expect(url).toBe("https://translation.googleapis.com/language/translate/v2");
    expect((init.headers as Record<string, string>)["x-goog-api-key"]).toBe("g-key");
    expect(body).toEqual({ q: ["나구모？", "ドン"], source: "ja", target: "ko", format: "text" });
  });
  it("fails when the number of translations differs from the number of texts", async () => {
    const { fn } = fakeFetch(() => new Response(JSON.stringify({ data: { translations: [{ translatedText: "하나뿐" }] } })));
    await expect(googleEngine({ apiKey: "k" }, fn).translate(items, [], never)).rejects.toThrow("1 translations for 2 texts");
  });
});
