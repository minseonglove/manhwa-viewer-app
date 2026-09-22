import { env } from "cloudflare:test";
import { describe, expect, it } from "vitest";

describe("AppAttestKey durable object", () => {
  it("registers a key once and advances only forward, for its own device", async () => {
    const stub = env.APP_ATTEST_KEYS.get(env.APP_ATTEST_KEYS.idFromName("k1"));
    expect(await stub.register("prod", "did", 1)).toBe("registered");
    expect(await stub.register("prod", "did", 2)).toBe("exists");
    expect(await stub.advance(1, "did")).toBe("ok");
    expect(await stub.advance(1, "did")).toBe("replay");
    expect(await stub.advance(0, "did")).toBe("replay");
    expect(await stub.advance(5, "other")).toBe("device_mismatch");
    expect(await stub.advance(5, "did")).toBe("ok");
  });
  it("does not know a key that was never registered", async () => {
    const stub = env.APP_ATTEST_KEYS.get(env.APP_ATTEST_KEYS.idFromName("k2"));
    expect(await stub.advance(1, "did")).toBe("unknown");
  });
});
