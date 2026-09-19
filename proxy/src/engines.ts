import type { Engine } from "./chain";
import { applyGlossaryToSource } from "./glossary";
import { parseOutput } from "./parse";
import { buildMessages } from "./prompt";
import type { AiBinding, Item } from "./types";

// Starting points taken from the measured latency distributions (30 pages each):
// Gemma on Workers AI mean 3.4s / max 7.8s, DeepSeek on DeepInfra mean 3-6s / p90 9-12s, Google NMT mean 0.4s / max 0.6s.
const TIMEOUTS = { gemma: 8_000, deepseek: 12_000, google: 3_000 } as const;

interface ChatEngineConfig {
  name: string;
  timeoutMs: number;
  url: string;
  apiKey: string;
  model: string;
  extraBody?: Record<string, unknown>;
}

interface ChatCompletion {
  choices?: Array<{ finish_reason?: string; message?: { content?: string | null; refusal?: string | null } }>;
}

function readChatCompletion(completion: ChatCompletion, items: Item[]): Record<string, string> {
  const choice = completion.choices?.[0];
  if (choice?.message?.refusal || choice?.finish_reason === "content_filter") throw new Error("refused");
  const { translations, problems } = parseOutput(choice?.message?.content, items.map((it) => it.id));
  // JSON.parse quotes the offending input in its message, which would be the model's text.
  if (problems.length) throw new Error(problems[0]!.startsWith("invalid json") ? "invalid json" : problems[0]);
  return translations;
}

function chatEngine(config: ChatEngineConfig, fetchFn: typeof fetch): Engine {
  return {
    name: config.name,
    timeoutMs: config.timeoutMs,
    async translate(items, glossary, signal) {
      const response = await fetchFn(config.url, {
        method: "POST",
        signal,
        headers: { authorization: `Bearer ${config.apiKey}`, "content-type": "application/json" },
        body: JSON.stringify({
          model: config.model,
          messages: buildMessages(items, glossary),
          temperature: 0.2,
          response_format: { type: "json_object" },
          ...config.extraBody,
        }),
      });
      // Providers echo parts of the request in error bodies; the reason must never carry page text into logs or responses.
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return readChatCompletion((await response.json()) as ChatCompletion, items);
    },
  };
}

export function gemmaEngine(ai: AiBinding): Engine {
  return {
    name: "gemma",
    timeoutMs: TIMEOUTS.gemma,
    async translate(items, glossary) {
      const completion = await ai.run("@cf/google/gemma-4-26b-a4b-it", {
        messages: buildMessages(items, glossary),
        temperature: 0.2,
        response_format: { type: "json_object" },
        // Gemma 4 thinks by default and bills the thinking as output. Measured through this binding:
        // 422 vs 14 completion tokens and 12.7s vs 3.8s for the same line. The model page does not document the switch, but it is honoured.
        chat_template_kwargs: { enable_thinking: false },
      });
      return readChatCompletion(completion as ChatCompletion, items);
    },
  };
}

export function deepseekEngine(config: { apiKey: string }, fetchFn: typeof fetch): Engine {
  return chatEngine(
    {
      name: "deepseek",
      timeoutMs: TIMEOUTS.deepseek,
      url: "https://api.deepinfra.com/v1/openai/chat/completions",
      apiKey: config.apiKey,
      model: "deepseek-ai/DeepSeek-V4-Flash",
    },
    fetchFn,
  );
}

export function googleEngine(config: { apiKey: string }, fetchFn: typeof fetch): Engine {
  return {
    name: "google",
    timeoutMs: TIMEOUTS.google,
    async translate(items, glossary, signal) {
      const response = await fetchFn("https://translation.googleapis.com/language/translate/v2", {
        method: "POST",
        signal,
        // A key in the query string ends up in URLs that get logged.
        headers: { "x-goog-api-key": config.apiKey, "content-type": "application/json" },
        // format=text: the default (html) returns HTML-escaped entities.
        body: JSON.stringify({ q: items.map((it) => applyGlossaryToSource(it.text, glossary)), source: "ja", target: "ko", format: "text" }),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const translated = ((await response.json()) as { data?: { translations?: Array<{ translatedText: string }> } }).data?.translations ?? [];
      if (translated.length !== items.length) throw new Error(`${translated.length} translations for ${items.length} texts`);
      return Object.fromEntries(items.map((it, i) => [it.id, translated[i]!.translatedText]));
    },
  };
}
