import { KINDS, type Item, type Kind, type TranslateRequest } from "./types";

export const REQUEST_LIMITS = { maxItems: 64, maxItemChars: 400, maxTotalChars: 4000, maxIdLength: 16 } as const;

export type Validation = { ok: true; request: TranslateRequest } | { ok: false; error: string };

export function validateTranslateRequest(body: unknown): Validation {
  if (!body || typeof body !== "object") return { ok: false, error: "body must be a JSON object" };
  const { items, source, target, glossary } = body as Record<string, unknown>;
  if (source !== "ja" || target !== "ko") return { ok: false, error: "only ja -> ko is supported" };
  if (!Array.isArray(items) || items.length === 0) return { ok: false, error: "items must be a non-empty array" };
  if (items.length > REQUEST_LIMITS.maxItems) return { ok: false, error: `at most ${REQUEST_LIMITS.maxItems} items` };
  if (glossary !== undefined && !Array.isArray(glossary)) return { ok: false, error: "glossary must be an array" };

  const ids = new Set<string>();
  const clean: Item[] = [];
  let total = 0;
  for (const raw of items) {
    const { id, kind, text } = (raw ?? {}) as Record<string, unknown>;
    if (typeof id !== "string" || !id || id.length > REQUEST_LIMITS.maxIdLength || ids.has(id)) return { ok: false, error: "item ids must be unique short strings" };
    if (typeof kind !== "string" || !(KINDS as readonly string[]).includes(kind)) return { ok: false, error: `unknown kind for ${id}` };
    if (typeof text !== "string" || !text.trim()) return { ok: false, error: `empty text for ${id}` };
    if ([...text].length > REQUEST_LIMITS.maxItemChars) return { ok: false, error: `text too long for ${id}` };
    total += [...text].length;
    ids.add(id);
    clean.push({ id, kind: kind as Kind, text });
  }
  if (total > REQUEST_LIMITS.maxTotalChars) return { ok: false, error: `at most ${REQUEST_LIMITS.maxTotalChars} characters per request` };
  return { ok: true, request: { items: clean, source, target, glossary: (glossary as TranslateRequest["glossary"]) ?? [] } };
}
