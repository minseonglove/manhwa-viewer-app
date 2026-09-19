import { afterEach, describe, expect, it, vi } from "vitest";
import { ChainError, runChain, type Engine, type LatencyModel } from "../src/chain";

const items = [{ id: "t01", kind: "bubble" as const, text: "a" }, { id: "t02", kind: "bubble" as const, text: "b" }];
const full = { t01: "가", t02: "나" };
const DEADLINE = 10_000;
const flat = (timeoutMs: number): LatencyModel => ({ baseMs: 0, perItemMs: 0, marginMs: timeoutMs });
const engine = (name: string, impl: Engine["translate"], latency = flat(50)): Engine => ({ name, latency, translate: impl });
const hangUntilAborted: Engine["translate"] = (_i, _g, signal) => new Promise((_res, rej) => signal.addEventListener("abort", () => rej(new Error("aborted"))));

afterEach(() => vi.useRealTimers());

describe("runChain", () => {
  it("returns the first engine's answer and does not call the others", async () => {
    let called = false;
    const r = await runChain([engine("gemma", async () => full), engine("deepseek", async () => { called = true; return full; })], items, [], DEADLINE);
    expect(r.engine).toBe("gemma");
    expect(r.translations).toEqual(full);
    expect(called).toBe(false);
  });
  it("falls through on an error and records why", async () => {
    const r = await runChain([engine("gemma", async () => { throw new Error("HTTP 500"); }), engine("deepseek", async () => full)], items, [], DEADLINE);
    expect(r.engine).toBe("deepseek");
    expect(r.attempts.map((a) => [a.engine, a.ok, a.reason])).toEqual([["gemma", false, "HTTP 500"], ["deepseek", true, undefined]]);
  });
  it("treats a missing id as a failed page, never a partial answer", async () => {
    const r = await runChain([engine("gemma", async () => ({ t01: "가" })), engine("google", async () => full)], items, [], DEADLINE);
    expect(r.engine).toBe("google");
    expect(r.attempts[0]!.reason).toBe("missing ids: t02");
  });
  it("aborts a slow engine at its timeout and moves on", async () => {
    let aborted = false;
    const slow = engine("gemma", (_i, _g, signal) => new Promise((_res, rej) => signal.addEventListener("abort", () => { aborted = true; rej(new Error("aborted")); })), flat(20));
    const r = await runChain([slow, engine("google", async () => full)], items, [], DEADLINE);
    expect(r.engine).toBe("google");
    expect(r.attempts[0]!.reason).toBe("timeout after 20ms");
    expect(aborted).toBe(true);
  });
  it("throws with every attempt when all engines fail", async () => {
    const err = await runChain([engine("gemma", async () => { throw new Error("x"); }), engine("google", async () => { throw new Error("y"); })], items, [], DEADLINE).catch((e) => e);
    expect(err).toBeInstanceOf(ChainError);
    expect((err as ChainError).attempts.map((a) => a.reason)).toEqual(["x", "y"]);
  });
});

describe("runChain time budget", () => {
  it("gives a page with more items a longer timeout", async () => {
    vi.useFakeTimers();
    const gemma = engine("gemma", hangUntilAborted, { baseMs: 1000, perItemMs: 100, marginMs: 500 });
    const pending = runChain([gemma, engine("google", async () => full)], items, [], DEADLINE);
    await vi.advanceTimersByTimeAsync(1700);
    expect((await pending).attempts[0]!.reason).toBe("timeout after 1700ms");
  });
  it("cuts an engine short so that the last engine still has its whole timeout before the deadline", async () => {
    vi.useFakeTimers();
    const pending = runChain([engine("gemma", hangUntilAborted, flat(9000)), engine("google", async () => full, flat(3000))], items, [], DEADLINE);
    await vi.advanceTimersByTimeAsync(7000);
    const r = await pending;
    expect(r.engine).toBe("google");
    expect(r.attempts[0]!.reason).toBe("timeout after 7000ms");
  });
  it("skips an engine that is not expected to finish in the time left, without calling it", async () => {
    vi.useFakeTimers();
    let called = false;
    const deepseek = engine("deepseek", async () => { called = true; return full; }, { baseMs: 3000, perItemMs: 0, marginMs: 4000 });
    const pending = runChain([engine("gemma", hangUntilAborted, flat(5000)), deepseek, engine("google", async () => full, flat(3000))], items, [], DEADLINE);
    await vi.advanceTimersByTimeAsync(5000);
    const r = await pending;
    expect(r.engine).toBe("google");
    expect(called).toBe(false);
    expect(r.attempts[1]).toEqual({ engine: "deepseek", ok: false, ms: 0, reason: "skipped: 2000ms left, expects 3000ms" });
  });
  it("runs an engine on a shortened timeout when its expected time still fits", async () => {
    vi.useFakeTimers();
    const deepseek = engine("deepseek", hangUntilAborted, { baseMs: 1500, perItemMs: 0, marginMs: 4000 });
    const pending = runChain([engine("gemma", hangUntilAborted, flat(5000)), deepseek, engine("google", async () => full, flat(3000))], items, [], DEADLINE);
    await vi.advanceTimersByTimeAsync(7000);
    const r = await pending;
    expect(r.engine).toBe("google");
    expect(r.attempts[1]!.reason).toBe("timeout after 2000ms");
  });
  it("never skips the last engine, however little time is left", async () => {
    vi.useFakeTimers();
    const google = engine("google", async () => full, { baseMs: 400, perItemMs: 0, marginMs: 2600 });
    const gemma = engine("gemma", hangUntilAborted, { baseMs: 1000, perItemMs: 0, marginMs: 4000 });
    const r = await runChain([gemma, google], items, [], 3000);
    expect(r.attempts.map((a) => [a.engine, a.ok])).toEqual([["gemma", false], ["google", true]]);
  });
});
