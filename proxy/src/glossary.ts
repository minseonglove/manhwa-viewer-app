import type { GlossaryEntry, Item } from "./types";

export const GLOSSARY_LIMITS = { maxEntries: 20, minSrcLength: 2, maxSrcLength: 20, maxKoLength: 20 } as const;

// "=" and "," delimit the glossary line in the prompt; a control character could start a new instruction line.
const UNSAFE = /[=,\p{Cc}]/u;

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Vertical text puts a line break inside a name, so every character may be followed by one.
function lineBreakTolerant(src: string): RegExp {
  return new RegExp([...src].map(escapeRegExp).join("\\n?"), "g");
}

export function filterGlossary(entries: unknown, items: Item[]): GlossaryEntry[] {
  if (!Array.isArray(entries)) return [];
  const texts = items.map((it) => it.text.replace(/\n/g, ""));
  const seen = new Set<string>();
  const kept: GlossaryEntry[] = [];
  for (const entry of entries) {
    if (kept.length >= GLOSSARY_LIMITS.maxEntries) break;
    const src = (entry as GlossaryEntry | null)?.src;
    const ko = (entry as GlossaryEntry | null)?.ko;
    if (typeof src !== "string" || typeof ko !== "string") continue;
    // A one-character source matches on nearly every page, so a crafted glossary could fill the cap on each request.
    if ([...src].length < GLOSSARY_LIMITS.minSrcLength || [...src].length > GLOSSARY_LIMITS.maxSrcLength) continue;
    if (ko.length === 0 || [...ko].length > GLOSSARY_LIMITS.maxKoLength) continue;
    if (UNSAFE.test(src) || UNSAFE.test(ko) || seen.has(src)) continue;
    if (!texts.some((t) => t.includes(src))) continue;
    seen.add(src);
    kept.push({ src, ko });
  }
  return kept;
}

/** For engines that take no prompt: an NMT engine passes Hangul in the source through unchanged. */
export function applyGlossaryToSource(text: string, glossary: GlossaryEntry[]): string {
  return [...glossary]
    .sort((a, b) => b.src.length - a.src.length)
    .reduce((acc, g) => acc.replace(lineBreakTolerant(g.src), g.ko), text);
}
