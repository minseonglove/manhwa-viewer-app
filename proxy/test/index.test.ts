import { beforeEach, describe, expect, it } from "vitest";
import { createHandler } from "../src/index";
import { b64url } from "../src/crypto";
import type { Env } from "../src/types";

const gemmaCalls: string[] = [];
let gemmaReply: () => unknown = () => ({ choices: [{ finish_reason: "stop", message: { content: '{"t01":"헉"}' } }] });
const env: Env = {
  SESSION_SIGNING_KEY: "sign", DEEPINFRA_API_KEY: "di", GOOGLE_TRANSLATE_API_KEY: "g", DEV_ATTESTATION_SECRET: "dev-secret",
  ANDROID_PACKAGE_NAME: "com.manhwaviewer.android", IOS_APP_ID: "PC497J52FF.com.manhwaviewer.app",
  AI: { run: async (model: string) => { gemmaCalls.push(model); return gemmaReply(); } },
};
const page = { items: [{ id: "t01", kind: "bubble", text: "はっ" }], source: "ja", target: "ko" };
const post = (path: string, body: unknown, token?: string) =>
  new Request(`https://proxy.example${path}`, { method: "POST", headers: token ? { authorization: `Bearer ${token}` } : {}, body: JSON.stringify(body) });

function upstream(handlers: Record<string, () => Response>) {
  const urls: string[] = [];
  const fn = (async (url: string) => {
    urls.push(url);
    const key = Object.keys(handlers).find((k) => url.includes(k));
    return key ? handlers[key]!() : new Response("unexpected", { status: 599 });
  }) as unknown as typeof fetch;
  return { fn, urls };
}
const chat = (content: string) => new Response(JSON.stringify({ choices: [{ finish_reason: "stop", message: { content } }] }));
const google = () => new Response(JSON.stringify({ data: { translations: [{ translatedText: "헉(구글)" }] } }));

beforeEach(() => {
  gemmaCalls.length = 0;
  gemmaReply = () => ({ choices: [{ finish_reason: "stop", message: { content: '{"t01":"헉"}' } }] });
});

async function session(handler: ReturnType<typeof createHandler>, body: unknown, e: Env = env) {
  const res = await handler(post("/v1/session", body), e);
  return { status: res.status, json: (await res.json()) as { token: string; tier: string } };
}

describe("POST /v1/session", () => {
  it("grants the attested tier for a valid development attestation", async () => {
    const { json } = await session(createHandler(upstream({}).fn), { platform: "dev", deviceId: "d1", secret: "dev-secret" });
    expect(json.tier).toBe("attested");
  });
  it("grants only the unattested tier otherwise", async () => {
    const handler = createHandler(upstream({}).fn);
    expect((await session(handler, { platform: "dev", deviceId: "d1", secret: "wrong" })).json.tier).toBe("unattested");
    expect((await session(handler, { platform: "android", deviceId: "d1", token: "x" })).json.tier).toBe("unattested");
  });
  it("ignores the development attestation when no development secret is configured", async () => {
    const prod = { ...env, DEV_ATTESTATION_SECRET: undefined };
    expect((await session(createHandler(upstream({}).fn), { platform: "dev", deviceId: "d1", secret: "" }, prod)).json.tier).toBe("unattested");
  });
});

describe("POST /v1/translate", () => {
  it("rejects a request without a valid session token", async () => {
    const handler = createHandler(upstream({}).fn);
    expect((await handler(post("/v1/translate", page), env)).status).toBe(401);
    expect((await handler(post("/v1/translate", page, "garbage"), env)).status).toBe(401);
  });
  it("rejects a malformed request before calling any engine", async () => {
    const up = upstream({});
    const handler = createHandler(up.fn);
    const { json } = await session(handler, { platform: "dev", deviceId: "d1", secret: "dev-secret" });
    expect((await handler(post("/v1/translate", { ...page, items: [] }, json.token), env)).status).toBe(400);
    expect(up.urls).toEqual([]);
  });
  it("serves an attested device from the first engine", async () => {
    const up = upstream({});
    const handler = createHandler(up.fn);
    const { json } = await session(handler, { platform: "dev", deviceId: "d1", secret: "dev-secret" });
    const res = await handler(post("/v1/translate", page, json.token), env);
    expect(await res.json()).toEqual({ translations: { t01: "헉" }, engine: "gemma" });
    expect(gemmaCalls).toHaveLength(1);
    expect(up.urls).toEqual([]);
  });
  it("walks the chain for an attested device when engines fail", async () => {
    gemmaReply = () => { throw new Error("binding unavailable"); };
    const up = upstream({ "api.deepinfra.com": () => chat("{}"), "translation.googleapis.com": google });
    const handler = createHandler(up.fn);
    const { json } = await session(handler, { platform: "dev", deviceId: "d1", secret: "dev-secret" });
    const res = await handler(post("/v1/translate", page, json.token), env);
    expect(await res.json()).toEqual({ translations: { t01: "헉(구글)" }, engine: "google" });
    expect(gemmaCalls).toHaveLength(1);
    expect(up.urls.map((u) => new URL(u).host)).toEqual(["api.deepinfra.com", "translation.googleapis.com"]);
  });
  it("refuses an unattested device without calling any engine", async () => {
    const up = upstream({ "translation.googleapis.com": google });
    const handler = createHandler(up.fn);
    const { json } = await session(handler, { platform: "android", deviceId: "d1", token: "unverifiable" });
    const res = await handler(post("/v1/translate", page, json.token), env);
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({ error: "attestation required" });
    expect(gemmaCalls).toEqual([]);
    expect(up.urls).toEqual([]);
  });
  it("answers 502 with the failure reasons, and none of the page text, when every engine fails", async () => {
    gemmaReply = () => { throw new Error("binding unavailable"); };
    const up = upstream({});
    const handler = createHandler(up.fn);
    const { json } = await session(handler, { platform: "dev", deviceId: "d1", secret: "dev-secret" });
    const res = await handler(post("/v1/translate", page, json.token), env);
    const text = await res.text();
    expect(res.status).toBe(502);
    expect(JSON.parse(text).attempts.map((a: { engine: string }) => a.engine)).toEqual(["gemma", "deepseek", "google"]);
    expect(text).not.toContain("はっ");
  });
});

describe("routing", () => {
  it("answers 404 for unknown paths and 405 for other methods", async () => {
    const handler = createHandler(upstream({}).fn);
    expect((await handler(post("/nope", {}), env)).status).toBe(404);
    expect((await handler(new Request("https://proxy.example/v1/translate"), env)).status).toBe(405);
  });
});

describe("POST /v1/challenge", () => {
  it("hands out a challenge that /v1/session accepts", async () => {
    const handler = createHandler(upstream({}).fn);
    const res = await handler(post("/v1/challenge", {}), env);
    const body = (await res.json()) as { challenge: string; expiresIn: number };
    expect(body.expiresIn).toBe(300);
    expect(typeof body.challenge).toBe("string");
  });
});

describe("session responses", () => {
  async function androidDevRegistration() {
    const handler = createHandler(upstream({}).fn);
    const { challenge } = (await (await handler(post("/v1/challenge", {}), env)).json()) as { challenge: string };
    const pair = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
    const publicKey = b64url(new Uint8Array((await crypto.subtle.exportKey("spki", pair.publicKey)) as ArrayBuffer));
    const res = await handler(post("/v1/session", { deviceId: "d1", platform: "android", challenge, publicKey, devSecret: "dev-secret" }), env);
    return { handler, body: (await res.json()) as Record<string, unknown> };
  }
  it("shortens an unattested session caused by a server-side failure", async () => {
    // The service account key is not a real key, so signing the OAuth assertion fails: a server-side problem, not the device's.
    const handler = createHandler(upstream({}).fn);
    const { challenge } = (await (await handler(post("/v1/challenge", {}), env)).json()) as { challenge: string };
    const pair = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
    const publicKey = b64url(new Uint8Array((await crypto.subtle.exportKey("spki", pair.publicKey)) as ArrayBuffer));
    const res = await handler(post("/v1/session", { deviceId: "d1", platform: "android", challenge, publicKey, integrityToken: "t" }), { ...env, PLAY_INTEGRITY_SERVICE_ACCOUNT: '{"client_email":"a","private_key":"b"}' });
    const body = (await res.json()) as { tier: string; expiresIn: number };
    expect(body).toMatchObject({ tier: "unattested", expiresIn: 600 });
  });
  it("gives a full-length session to an unattested device when the device, not the server, was the problem", async () => {
    const { json } = await session(createHandler(upstream({}).fn), { platform: "dev", deviceId: "d1", secret: "wrong" });
    expect(json).toMatchObject({ tier: "unattested", expiresIn: 43_200 });
  });
  it("returns the credential and its recheck interval to an Android device that registers on a challenge from /v1/challenge", async () => {
    const res = await androidDevRegistration();
    expect(res.body).toMatchObject({ tier: "attested", expiresIn: 43_200, recheckAfter: 604_800 });
    expect(typeof res.body.credential).toBe("string");
    expect(res.body.credentialStatus).toBeUndefined();
  });
  it("tells a device that its credential is invalid, on a short session so it registers again soon", async () => {
    const { handler, body } = await androidDevRegistration();
    const present = async (deviceId: string, credential: string) => {
      const { challenge } = (await (await handler(post("/v1/challenge", {}), env)).json()) as { challenge: string };
      const res = await handler(post("/v1/session", { deviceId, platform: "android", challenge, credential, signature: "AAAA" }), env);
      return (await res.json()) as Record<string, unknown>;
    };
    for (const out of [await present("d1", `${body.credential}x`), await present("another-install", String(body.credential))]) {
      expect(out).toMatchObject({ tier: "unattested", expiresIn: 600, credentialStatus: "invalid" });
      expect(out.credential).toBeUndefined();
    }
  });
  it("gives a full-length session to a device that sends no attestation at all", async () => {
    const { json } = await session(createHandler(upstream({}).fn), { deviceId: "d1" });
    expect(json).toMatchObject({ tier: "unattested", expiresIn: 43_200 });
  });
  it("logs a fixed reason and none of the identifiers or tokens", async () => {
    const lines: string[] = [];
    const original = console.log;
    console.log = (line: string) => lines.push(line);
    try {
      await session(createHandler(upstream({}).fn), { platform: "dev", deviceId: "device-secret-id", secret: "dev-secret" });
    } finally {
      console.log = original;
    }
    expect(JSON.parse(lines.at(-1)!)).toMatchObject({ event: "session", platform: "dev", tier: "attested", reason: "dev_ok" });
    expect(lines.join("\n")).not.toContain("device-secret-id");
    expect(lines.join("\n")).not.toContain("dev-secret");
  });
});
