import type { GlossaryEntry, Item } from "./types";

// Latency grows with the number of items on the page, so a fixed timeout is either too short for a dense page or too long for a sparse one.
export interface LatencyModel {
  baseMs: number;
  perItemMs: number;
  marginMs: number;
}

export interface Engine {
  name: string;
  latency: LatencyModel;
  translate(items: Item[], glossary: GlossaryEntry[], signal: AbortSignal): Promise<Record<string, string>>;
}

export const expectedMs = (latency: LatencyModel, itemCount: number) => latency.baseMs + latency.perItemMs * itemCount;
export const timeoutMs = (latency: LatencyModel, itemCount: number) => expectedMs(latency, itemCount) + latency.marginMs;

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

async function attempt(engine: Engine, items: Item[], glossary: GlossaryEntry[], limitMs: number): Promise<Record<string, string>> {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => {
      // Reject before aborting: abort() makes the engine's own promise reject in the same tick,
      // and whichever rejection comes first is the reason that gets recorded.
      reject(new Error(`timeout after ${limitMs}ms`));
      controller.abort();
    }, limitMs);
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

export async function runChain(engines: Engine[], items: Item[], glossary: GlossaryEntry[], deadlineMs: number): Promise<ChainResult> {
  const attempts: Attempt[] = [];
  const chainStarted = Date.now();
  const last = engines[engines.length - 1];
  // The last engine is the one that has to answer when nothing else did, so the others must be done early enough to leave it its whole timeout.
  const othersEndBy = deadlineMs - (last ? timeoutMs(last.latency, items.length) : 0);
  for (const engine of engines) {
    const started = Date.now();
    let limitMs = timeoutMs(engine.latency, items.length);
    if (engine !== last) {
      const left = Math.max(0, othersEndBy - (started - chainStarted));
      const expected = expectedMs(engine.latency, items.length);
      // Starting an engine that will most likely be cut off only makes the reader wait longer for the same fallback.
      if (left < expected) {
        attempts.push({ engine: engine.name, ok: false, ms: 0, reason: `skipped: ${left}ms left, expects ${expected}ms` });
        continue;
      }
      limitMs = Math.min(limitMs, left);
    }
    try {
      const translations = await attempt(engine, items, glossary, limitMs);
      attempts.push({ engine: engine.name, ok: true, ms: Date.now() - started });
      return { translations, engine: engine.name, attempts };
    } catch (e) {
      attempts.push({ engine: engine.name, ok: false, ms: Date.now() - started, reason: (e as Error).message });
    }
  }
  throw new ChainError(attempts);
}
