import { describe, expect, it } from "vitest";
import { validateTranslateRequest } from "../src/limits";

const ok = { items: [{ id: "t01", kind: "bubble", text: "はっ" }], source: "ja", target: "ko" };

describe("validateTranslateRequest", () => {
  it("accepts a well-formed request", () => {
    expect(validateTranslateRequest(ok)).toEqual({ ok: true, request: { ...ok, glossary: [] } });
  });
  it.each([
    ["no items", { ...ok, items: [] }],
    ["too many items", { ...ok, items: Array.from({ length: 65 }, (_, i) => ({ id: `t${i}`, kind: "bubble", text: "a" })) }],
    ["duplicate ids", { ...ok, items: [ok.items[0], ok.items[0]] }],
    ["unknown kind", { ...ok, items: [{ id: "t01", kind: "system", text: "a" }] }],
    ["blank text", { ...ok, items: [{ id: "t01", kind: "bubble", text: "  " }] }],
    ["item too long", { ...ok, items: [{ id: "t01", kind: "bubble", text: "あ".repeat(401) }] }],
    ["page too long", { ...ok, items: Array.from({ length: 11 }, (_, i) => ({ id: `t${i}`, kind: "bubble", text: "あ".repeat(400) })) }],
    ["unsupported pair", { ...ok, source: "ko", target: "ja" }],
    ["glossary not an array", { ...ok, glossary: "x" }],
    ["not an object", "x"],
  ])("rejects %s", (_name, body) => {
    expect(validateTranslateRequest(body).ok).toBe(false);
  });
});
