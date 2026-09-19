import type { GlossaryEntry, Item } from "./types";

export interface Engine {
  name: string;
  timeoutMs: number;
  translate(items: Item[], glossary: GlossaryEntry[], signal: AbortSignal): Promise<Record<string, string>>;
}

export interface Attempt {
  engine: string;
  ok: boolean;
  ms: number;
  reason?: string;
}

export interface ChainResult {
  translations: Record<string, string>;
  engine: string;
  attempts: Attempt[];
}

export class ChainError extends Error {
  constructor(public readonly attempts: Attempt[]) {
    super("all translation engines failed");
  }
}

async function attempt(engine: Engine, items: Item[], glossary: GlossaryEntry[]): Promise<Record<string, string>> {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      // Reject before aborting: abort() makes the engine's own promise reject in the same tick,
      // and whichever rejection comes first is the reason that gets recorded.
      reject(new Error(`timeout after ${engine.timeoutMs}ms`));
      controller.abort();
    }, engine.timeoutMs);
  });
  try {
    const translations = await Promise.race([engine.translate(items, glossary, controller.signal), timeout]);
    // A page with a hole is how answers end up shifted by one balloon; the whole page goes to the next engine instead.
    const missing = items.filter((it) => typeof translations[it.id] !== "string").map((it) => it.id);
    if (missing.length) throw new Error(`missing ids: ${missing.join(",")}`);
    return Object.fromEntries(items.map((it) => [it.id, translations[it.id]!]));
  } finally {
    if (timer !== undefined) clearTimeout(timer);
  }
}

export async function runChain(engines: Engine[], items: Item[], glossary: GlossaryEntry[]): Promise<ChainResult> {
  const attempts: Attempt[] = [];
  for (const engine of engines) {
    const started = Date.now();
    try {
      const translations = await attempt(engine, items, glossary);
      attempts.push({ engine: engine.name, ok: true, ms: Date.now() - started });
      return { translations, engine: engine.name, attempts };
    } catch (e) {
      attempts.push({ engine: engine.name, ok: false, ms: Date.now() - started, reason: (e as Error).message });
    }
  }
  throw new ChainError(attempts);
}
