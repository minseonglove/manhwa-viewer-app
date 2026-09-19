import { describe, expect, it } from "vitest";
import { ChainError, runChain, type Engine } from "../src/chain";

const items = [{ id: "t01", kind: "bubble" as const, text: "a" }, { id: "t02", kind: "bubble" as const, text: "b" }];
const full = { t01: "가", t02: "나" };
const engine = (name: string, impl: Engine["translate"], timeoutMs = 50): Engine => ({ name, timeoutMs, translate: impl });

describe("runChain", () => {
  it("returns the first engine's answer and does not call the others", async () => {
    let called = false;
    const r = await runChain([engine("gemma", async () => full), engine("deepseek", async () => { called = true; return full; })], items, []);
    expect(r.engine).toBe("gemma");
    expect(r.translations).toEqual(full);
    expect(called).toBe(false);
  });
  it("falls through on an error and records why", async () => {
    const r = await runChain([engine("gemma", async () => { throw new Error("HTTP 500"); }), engine("deepseek", async () => full)], items, []);
    expect(r.engine).toBe("deepseek");
    expect(r.attempts.map((a) => [a.engine, a.ok, a.reason])).toEqual([["gemma", false, "HTTP 500"], ["deepseek", true, undefined]]);
  });
  it("treats a missing id as a failed page, never a partial answer", async () => {
    const r = await runChain([engine("gemma", async () => ({ t01: "가" })), engine("google", async () => full)], items, []);
    expect(r.engine).toBe("google");
    expect(r.attempts[0]!.reason).toBe("missing ids: t02");
  });
  it("aborts a slow engine at its timeout and moves on", async () => {
    let aborted = false;
    const slow = engine("gemma", (_i, _g, signal) => new Promise((_res, rej) => signal.addEventListener("abort", () => { aborted = true; rej(new Error("aborted")); })), 20);
    const r = await runChain([slow, engine("google", async () => full)], items, []);
    expect(r.engine).toBe("google");
    expect(r.attempts[0]!.reason).toBe("timeout after 20ms");
    expect(aborted).toBe(true);
  });
  it("throws with every attempt when all engines fail", async () => {
    const err = await runChain([engine("gemma", async () => { throw new Error("x"); }), engine("google", async () => { throw new Error("y"); })], items, []).catch((e) => e);
    expect(err).toBeInstanceOf(ChainError);
    expect((err as ChainError).attempts.map((a) => a.reason)).toEqual(["x", "y"]);
  });
});
