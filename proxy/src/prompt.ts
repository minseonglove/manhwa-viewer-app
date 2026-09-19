import { SYSTEM_PROMPT_V2_1 } from "./prompt.generated";
import type { GlossaryEntry, Item } from "./types";

export interface ChatMessage {
  role: "system" | "user";
  content: string;
}

export function buildMessages(items: Item[], glossary: GlossaryEntry[]): ChatMessage[] {
  // The glossary goes last: providers bill a cached prompt prefix at a fraction of the price,
  // and anything variable placed before the fixed rules would break that prefix on every page.
  const suffix = glossary.length
    ? "\n\nGlossary (always use these Korean spellings for names): " + glossary.map((g) => `${g.src}=${g.ko}`).join(", ")
    : "";
  return [
    { role: "system", content: SYSTEM_PROMPT_V2_1 + suffix },
    { role: "user", content: items.map((it) => `${it.id}\t${it.kind}\t${it.text}`).join("\n") },
  ];
}
