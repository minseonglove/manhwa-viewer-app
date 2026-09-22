import { describe, expect, it } from "vitest";
import { decodeIntegrityToken, evaluateVerdict } from "../src/attestation/play-integrity";

const NOW = 1_800_000_000_000;
const PKG = "com.manhwaviewer.android";
const HASH = "jqsT_pFpJF4MPnUueOENRh9B-Usjg-Uw9oldDTBN3Mo";

function verdict(overrides: Record<string, unknown> = {}) {
  return {
    requestDetails: { requestPackageName: PKG, requestHash: HASH, timestampMillis: String(NOW - 1000) },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG, certificateSha256Digest: ["x"], versionCode: "1" },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
    ...overrides,
  };
}
const expected = { packageName: PKG, requestHash: HASH, nowMs: NOW };

describe("evaluateVerdict", () => {
  it("accepts a Play-recognized, licensed app on a device meeting device integrity", () => {
    expect(evaluateVerdict(verdict(), expected)).toBe("ok");
    expect(evaluateVerdict(verdict({ deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY", "MEETS_DEVICE_INTEGRITY", "MEETS_STRONG_INTEGRITY"] } }), expected)).toBe("ok");
  });
  it("names the first failed check", () => {
    const req = (r: Record<string, unknown>) => verdict({ requestDetails: { requestPackageName: PKG, requestHash: HASH, timestampMillis: String(NOW), ...r } });
    expect(evaluateVerdict(req({ requestPackageName: "com.other" }), expected)).toBe("pi_package");
    expect(evaluateVerdict(req({ requestHash: "other" }), expected)).toBe("pi_hash");
    expect(evaluateVerdict(req({ timestampMillis: String(NOW - 300_001) }), expected)).toBe("pi_stale");
    expect(evaluateVerdict(req({ timestampMillis: String(NOW + 60_001) }), expected)).toBe("pi_stale");
    expect(evaluateVerdict(req({ timestampMillis: "soon" }), expected)).toBe("pi_stale");
    expect(evaluateVerdict(verdict({ appIntegrity: { appRecognitionVerdict: "UNRECOGNIZED_VERSION", packageName: PKG } }), expected)).toBe("pi_app");
    expect(evaluateVerdict(verdict({ appIntegrity: { appRecognitionVerdict: "UNEVALUATED" } }), expected)).toBe("pi_app");
    expect(evaluateVerdict(verdict({ deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_BASIC_INTEGRITY"] } }), expected)).toBe("pi_device");
    expect(evaluateVerdict(verdict({ deviceIntegrity: {} }), expected)).toBe("pi_device");
    expect(evaluateVerdict(verdict({ accountDetails: { appLicensingVerdict: "UNLICENSED" } }), expected)).toBe("pi_license");
  });
  it("fails a replayed token, which Google answers with emptied verdicts", () => {
    expect(evaluateVerdict(verdict({ appIntegrity: { appRecognitionVerdict: "UNEVALUATED" }, deviceIntegrity: {}, accountDetails: { appLicensingVerdict: "UNEVALUATED" } }), expected)).toBe("pi_app");
  });
  it("fails garbage", () => {
    expect(evaluateVerdict(null, expected)).toBe("pi_package");
    expect(evaluateVerdict("x", expected)).toBe("pi_package");
  });
});

describe("decodeIntegrityToken", () => {
  const call = (reply: () => Response | Promise<Response>) => {
    const seen: { url: string; init?: RequestInit }[] = [];
    const fn = (async (url: string, init?: RequestInit) => {
      seen.push({ url, init });
      return reply();
    }) as unknown as typeof fetch;
    return { fn, seen };
  };

  it("posts the token with the access token and returns the payload", async () => {
    const c = call(() => new Response(JSON.stringify({ tokenPayloadExternal: verdict() })));
    expect(await decodeIntegrityToken(c.fn, "at", PKG, "tok")).toEqual({ kind: "payload", payload: verdict() });
    expect(c.seen[0]!.url).toBe(`https://playintegrity.googleapis.com/v1/${PKG}:decodeIntegrityToken`);
    expect(new Headers(c.seen[0]!.init!.headers).get("authorization")).toBe("Bearer at");
    expect(JSON.parse(String(c.seen[0]!.init!.body))).toEqual({ integrityToken: "tok" });
  });
  it("classifies failures", async () => {
    const transient = { kind: "transient", quotaExceeded: false, accessTokenRejected: false };
    expect(await decodeIntegrityToken(call(() => new Response("{}", { status: 400 })).fn, "at", PKG, "t")).toEqual({ kind: "invalid" });
    expect(await decodeIntegrityToken(call(() => new Response("{}", { status: 429 })).fn, "at", PKG, "t")).toEqual({ ...transient, quotaExceeded: true });
    for (const status of [401, 403]) {
      expect(await decodeIntegrityToken(call(() => new Response("{}", { status })).fn, "at", PKG, "t")).toEqual({ ...transient, accessTokenRejected: true });
    }
    for (const status of [404, 500, 503]) {
      expect(await decodeIntegrityToken(call(() => new Response("{}", { status })).fn, "at", PKG, "t")).toEqual(transient);
    }
    expect(await decodeIntegrityToken(call(() => Promise.reject(new Error("net"))).fn, "at", PKG, "t")).toEqual(transient);
    expect(await decodeIntegrityToken(call(() => new Response("not json")).fn, "at", PKG, "t")).toEqual(transient);
  });
  it("gives up after its timeout", async () => {
    const hang = (async (_url: string, init?: RequestInit) =>
      new Promise<Response>((_, reject) => init?.signal?.addEventListener("abort", () => reject(new Error("aborted"))))) as unknown as typeof fetch;
    expect(await decodeIntegrityToken(hang, "at", PKG, "t", 20)).toEqual({ kind: "transient", quotaExceeded: false, accessTokenRejected: false });
  });
});
