import { describe, expect, it } from "vitest";
import { SYSTEM_PROMPT_V2_1 } from "../src/prompt.generated";
import { buildMessages } from "../src/prompt";

describe("prompt", () => {
  it("is byte-identical to the prompt the quality measurements used", async () => {
    const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(SYSTEM_PROMPT_V2_1)));
    const hex = [...digest].map((b) => b.toString(16).padStart(2, "0")).join("");
    expect(SYSTEM_PROMPT_V2_1.length).toBe(1269);
    expect(hex.slice(0, 16)).toBe("a2c3d5d1c15bd16c");
  });

  it("sends one tab-separated line per item, in order", () => {
    const [, user] = buildMessages([{ id: "t01", kind: "bubble", text: "はっ" }, { id: "t02", kind: "sfx", text: "ドン" }], []);
    expect(user).toEqual({ role: "user", content: "t01\tbubble\tはっ\nt02\tsfx\tドン" });
  });

  it("appends the glossary after the fixed prompt so the cacheable prefix stays intact", () => {
    const [system] = buildMessages([{ id: "t01", kind: "bubble", text: "立涌？" }], [{ src: "立涌", ko: "다테와쿠" }, { src: "南雲", ko: "나구모" }]);
    expect(system!.content.startsWith(SYSTEM_PROMPT_V2_1)).toBe(true);
    expect(system!.content.slice(SYSTEM_PROMPT_V2_1.length)).toBe("\n\nGlossary (always use these Korean spellings for names): 立涌=다테와쿠, 南雲=나구모");
  });

  it("adds nothing when the glossary is empty", () => {
    expect(buildMessages([{ id: "t01", kind: "bubble", text: "a" }], [])[0]!.content).toBe(SYSTEM_PROMPT_V2_1);
  });
});
