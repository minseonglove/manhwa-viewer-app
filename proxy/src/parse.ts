export interface ParsedOutput {
  translations: Record<string, string>;
  problems: string[];
}

const FENCE = /^```(?:json)?\s*|\s*```$/gm;

export function parseOutput(content: string | null | undefined, expectedIds: string[]): ParsedOutput {
  if (!content) return { translations: {}, problems: ["empty response"] };
  let data: unknown;
  try {
    data = JSON.parse(content.trim().replace(FENCE, "").trim());
  } catch (e) {
    return { translations: {}, problems: [`invalid json: ${(e as Error).message}`] };
  }
  const entries: Array<[unknown, unknown]> = [];
  if (Array.isArray(data)) {
    for (const e of data) entries.push([(e as { id?: unknown })?.id, (e as { ko?: unknown })?.ko]);
  } else if (data && typeof data === "object") {
    const items = (data as { items?: unknown }).items;
    if (Array.isArray(items)) for (const e of items) entries.push([(e as { id?: unknown })?.id, (e as { ko?: unknown })?.ko]);
    else entries.push(...Object.entries(data as Record<string, unknown>));
  } else {
    return { translations: {}, problems: ["no items"] };
  }

  const translations: Record<string, string> = {};
  const problems: string[] = [];
  for (const [id, ko] of entries) {
    if (typeof id !== "string") problems.push("malformed entry");
    else if (typeof ko !== "string") problems.push(`${id}: not a string`);
    else translations[id] = ko;
  }
  const expected = new Set(expectedIds);
  const missing = expectedIds.filter((id) => !(id in translations));
  const extra = Object.keys(translations).filter((id) => !expected.has(id));
  if (missing.length) problems.push(`missing ids: ${missing.join(",")}`);
  if (extra.length) problems.push(`unexpected ids: ${extra.join(",")}`);
  return { translations, problems };
}
