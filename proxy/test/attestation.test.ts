import { beforeEach, describe, expect, it } from "vitest";
import { attest, deviceHash, type AttestationContext } from "../src/attestation";
import { verifyCredential } from "../src/attestation/credential";
import { GoogleAccessTokens } from "../src/attestation/google-oauth";
import type { KeyStore } from "../src/attestation/key-store-client";
import { memoryKeyStore } from "./helpers/memory-key-store";
import { computeBinding, issueChallenge } from "../src/challenge";
import { b64url, fromBase64 } from "../src/crypto";
import type { Env } from "../src/types";
import { appAttestFixture, makeAssertion, newKey } from "./helpers/pki";
import { rawSignatureToDer } from "./helpers/der";

const NOW_MS = Date.UTC(2026, 8, 22);
const NOW = NOW_MS / 1000;
const PKG = "com.manhwaviewer.android";
const APP_ID = "PC497J52FF.com.manhwaviewer.app";
const DEVICE = "0123456789abcdef0123456789abcdef";

let serviceAccount: string;
async function makeServiceAccount() {
  const pair = (await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign"])) as CryptoKeyPair;
  const pkcs8 = new Uint8Array((await crypto.subtle.exportKey("pkcs8", pair.privateKey)) as ArrayBuffer);
  return JSON.stringify({ client_email: "svc@x.iam.gserviceaccount.com", private_key: `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...pkcs8))}\n-----END PRIVATE KEY-----\n` });
}

const baseEnv = (): Env => ({
  AI: { run: async () => ({}) }, SESSION_SIGNING_KEY: "sign", DEEPINFRA_API_KEY: "", GOOGLE_TRANSLATE_API_KEY: "",
  ANDROID_PACKAGE_NAME: PKG, IOS_APP_ID: APP_ID, PLAY_INTEGRITY_SERVICE_ACCOUNT: serviceAccount,
});

/** Google's token and decode endpoints; the decode reply is built from the posted token so tests can steer the verdict. */
function google(decode: (token: string) => Response) {
  const calls: string[] = [];
  const fn = (async (url: string, init?: RequestInit) => {
    calls.push(new URL(url).host);
    if (url.startsWith("https://oauth2.googleapis.com/")) return new Response(JSON.stringify({ access_token: "at", expires_in: 3600 }));
    if (url.startsWith("https://playintegrity.googleapis.com/")) return decode(JSON.parse(String(init?.body)).integrityToken);
    return new Response("unexpected", { status: 599 });
  }) as unknown as typeof fetch;
  return { fn, calls };
}
const passingVerdict = (requestHash: string, overrides: Record<string, unknown> = {}) =>
  new Response(JSON.stringify({ tokenPayloadExternal: {
    requestDetails: { requestPackageName: PKG, requestHash, timestampMillis: String(NOW_MS - 1000) },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: PKG },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
    accountDetails: { appLicensingVerdict: "LICENSED" },
    ...overrides,
  } }));

async function ctx(fetchFn: typeof fetch, extra: Partial<AttestationContext> = {}): Promise<AttestationContext> {
  return {
    deviceId: DEVICE, did: await deviceHash(DEVICE), env: baseEnv(), fetchFn, keyStore: memoryKeyStore(),
    googleTokens: new GoogleAccessTokens(fetchFn), backoff: { untilMs: 0 }, nowMs: NOW_MS, clientIp: "203.0.113.1", ...extra,
  };
}

async function androidKey() {
  const pair = await newKey("P-256");
  const spki = new Uint8Array((await crypto.subtle.exportKey("spki", pair.publicKey)) as ArrayBuffer);
  const publicKey = b64url(spki);
  const sign = async (challenge: string) =>
    b64url(rawSignatureToDer(new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, pair.privateKey, await computeBinding(challenge, DEVICE, publicKey)))));
  return { publicKey, sign };
}

beforeEach(async () => {
  serviceAccount ??= await makeServiceAccount();
});

describe("android registration", () => {
  it("issues a credential when Google vouches for this binding", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const hash = b64url(await computeBinding(challenge, DEVICE, key.publicKey));
    const g = google(() => passingVerdict(hash));
    const out = await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "tok" }, await ctx(g.fn));
    expect(out).toMatchObject({ tier: "attested", reason: "ok", transient: false, recheckAfter: 604800 });
    const cred = await verifyCredential("sign", out.credential!);
    expect(cred).toMatchObject({ p: "a", pk: key.publicKey, d: await deviceHash(DEVICE), va: NOW });
    expect(g.calls).toEqual(["oauth2.googleapis.com", "playintegrity.googleapis.com"]);
  });
  it("refuses without a grace period when Google's verdict fails or the token is bad", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const hash = b64url(await computeBinding(challenge, DEVICE, key.publicKey));
    const failing = google(() => passingVerdict(hash, { deviceIntegrity: {} }));
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, await ctx(failing.fn))).toMatchObject({ tier: "unattested", reason: "pi_device", transient: false });
    const bad = google(() => new Response("{}", { status: 400 }));
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, await ctx(bad.fn))).toMatchObject({ tier: "unattested", reason: "pi_invalid_token", transient: false });
  });
  it("spends no Google call on a bad challenge", async () => {
    const g = google(() => new Response("{}"));
    const out = await attest({ platform: "android", challenge: "forged", publicKey: "x", integrityToken: "t" }, await ctx(g.fn));
    expect(out).toMatchObject({ tier: "unattested", reason: "challenge_invalid" });
    expect(g.calls).toEqual([]);
  });
  it("stops calling Google while rate limited or backing off after a 429", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const limited = google(() => new Response("{}"));
    const env = { ...baseEnv(), REGISTRATION_LIMITER: { limit: async () => ({ success: false }) } };
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, await ctx(limited.fn, { env }))).toMatchObject({ reason: "pi_rate_limited", transient: true });
    expect(limited.calls).toEqual([]);

    const quota = google(() => new Response("{}", { status: 429 }));
    const c = await ctx(quota.fn);
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, c)).toMatchObject({ reason: "pi_transient", transient: true });
    expect(c.backoff.untilMs).toBe(NOW_MS + 60_000);
    quota.calls.length = 0;
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, c)).toMatchObject({ reason: "pi_backoff", transient: true });
    expect(quota.calls).toEqual([]);
  });
  it("registers with the development secret only where one is configured", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const g = google(() => new Response("{}"));
    const dev = { ...baseEnv(), DEV_ATTESTATION_SECRET: "dev" };
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, devSecret: "dev" }, await ctx(g.fn, { env: dev }))).toMatchObject({ tier: "attested", reason: "dev_ok" });
    expect(await attest({ platform: "android", challenge, publicKey: key.publicKey, devSecret: "dev" }, await ctx(g.fn))).toMatchObject({ tier: "unattested", reason: "dev_denied" });
    expect(g.calls).toEqual([]);
  });
  it("refuses a wrong development secret even where one is configured", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const dev = { ...baseEnv(), DEV_ATTESTATION_SECRET: "dev" };
    const out = await attest({ platform: "android", challenge, publicKey: key.publicKey, devSecret: "wrong" }, await ctx(google(() => new Response("{}")).fn, { env: dev }));
    expect(out).toMatchObject({ tier: "unattested", reason: "dev_denied" });
    expect(out.credential).toBeUndefined();
  });
  it("limits registrations per client IP", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const hash = b64url(await computeBinding(challenge, DEVICE, key.publicKey));
    const keys: string[] = [];
    const env = { ...baseEnv(), REGISTRATION_LIMITER: { limit: async ({ key }: { key: string }) => { keys.push(key); return { success: true }; } } };
    await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, await ctx(google(() => passingVerdict(hash)).fn, { env, clientIp: "198.51.100.7" }));
    expect(keys).toEqual(["198.51.100.7"]);
  });
  it("carries on to Google when the rate limiter itself fails", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const hash = b64url(await computeBinding(challenge, DEVICE, key.publicKey));
    const g = google(() => passingVerdict(hash));
    const env = { ...baseEnv(), REGISTRATION_LIMITER: { limit: async () => { throw new Error("binding unavailable"); } } };
    const out = await attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" }, await ctx(g.fn, { env }));
    expect(out).toMatchObject({ tier: "attested", reason: "ok" });
    expect(g.calls).toEqual(["oauth2.googleapis.com", "playintegrity.googleapis.com"]);
  });
  it("mints a new Google access token after decode rejects the cached one, but keeps it through outages", async () => {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const hash = b64url(await computeBinding(challenge, DEVICE, key.publicKey));
    const body = { platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t" };
    for (const status of [401, 403]) {
      let reply = status;
      const g = google(() => (reply === 200 ? passingVerdict(hash) : new Response("{}", { status: reply })));
      const c = await ctx(g.fn);
      expect(await attest(body, c)).toMatchObject({ reason: "pi_transient", transient: true });
      reply = 200;
      expect(await attest(body, c)).toMatchObject({ tier: "attested", reason: "ok" });
      expect(g.calls).toEqual(["oauth2.googleapis.com", "playintegrity.googleapis.com", "oauth2.googleapis.com", "playintegrity.googleapis.com"]);
    }
    let outage = true;
    const down = google(() => (outage ? new Response("{}", { status: 503 }) : passingVerdict(hash)));
    const c = await ctx(down.fn);
    expect(await attest(body, c)).toMatchObject({ reason: "pi_transient", transient: true });
    outage = false;
    expect(await attest(body, c)).toMatchObject({ tier: "attested", reason: "ok" });
    expect(down.calls).toEqual(["oauth2.googleapis.com", "playintegrity.googleapis.com", "playintegrity.googleapis.com"]);
  });
  it("rejects a public key that is not a P-256 SPKI", async () => {
    const challenge = await issueChallenge("sign", NOW);
    const out = await attest({ platform: "android", challenge, publicKey: "AAAA", devSecret: "dev" }, await ctx(google(() => new Response("{}")).fn, { env: { ...baseEnv(), DEV_ATTESTATION_SECRET: "dev" } }));
    expect(out).toMatchObject({ tier: "unattested", reason: "key_invalid" });
  });
});

describe("android sessions on a credential", () => {
  async function registered() {
    const key = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const dev = { ...baseEnv(), DEV_ATTESTATION_SECRET: "dev" };
    const out = await attest({ platform: "android", challenge, publicKey: key.publicKey, devSecret: "dev" }, await ctx(google(() => new Response("{}")).fn, { env: dev }));
    return { key, credential: out.credential! };
  }

  it("attests from the signature alone, with no Google call, inside seven days", async () => {
    const { key, credential } = await registered();
    const challenge = await issueChallenge("sign", NOW);
    const g = google(() => new Response("{}"));
    const later = await ctx(g.fn, { nowMs: NOW_MS + 6 * 86_400_000 });
    const c2 = await issueChallenge("sign", NOW + 6 * 86_400);
    expect(await attest({ platform: "android", challenge: c2, credential, signature: await key.sign(c2) }, later)).toMatchObject({ tier: "attested", reason: "ok", recheckAfter: 86_400 });
    expect(g.calls).toEqual([]);
    expect(await attest({ platform: "android", challenge, credential, signature: await key.sign("other") }, await ctx(g.fn))).toMatchObject({ tier: "unattested", reason: "sig_invalid" });
  });
  it("requires a recheck after seven days and marks the credential for re-registration", async () => {
    const { key, credential } = await registered();
    const at = NOW + 7 * 86_400;
    const challenge = await issueChallenge("sign", at);
    const out = await attest({ platform: "android", challenge, credential, signature: await key.sign(challenge) }, await ctx(google(() => new Response("{}")).fn, { nowMs: at * 1000 }));
    expect(out).toMatchObject({ tier: "unattested", reason: "cred_expired", credentialStatus: "invalid" });
  });
  it("carries a device through Google outages for up to ten days, but not past them", async () => {
    const { key, credential } = await registered();
    const during = async (days: number) => {
      const at = NOW + days * 86_400;
      const challenge = await issueChallenge("sign", at);
      const down = google(() => new Response("{}", { status: 503 }));
      return attest({ platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t", credential, signature: await key.sign(challenge) }, await ctx(down.fn, { nowMs: at * 1000 }));
    };
    expect(await during(9)).toMatchObject({ tier: "attested", reason: "grace" });
    expect(await during(10)).toMatchObject({ tier: "unattested", reason: "cred_expired", transient: true, credentialStatus: "invalid" });
  });
  it("asks for re-registration when the signature does not come from the credential's key", async () => {
    const { credential } = await registered();
    const stranger = await androidKey();
    const challenge = await issueChallenge("sign", NOW);
    const out = await attest({ platform: "android", challenge, credential, signature: await stranger.sign(challenge) }, await ctx(google(() => new Response("{}")).fn));
    expect(out).toMatchObject({ tier: "unattested", reason: "sig_invalid", transient: false, credentialStatus: "invalid" });
  });
  it("gives no grace when Google rejects the device while it also presents a valid credential", async () => {
    const { key, credential } = await registered();
    const at = NOW + 8 * 86_400;
    const challenge = await issueChallenge("sign", at);
    const hash = b64url(await computeBinding(challenge, DEVICE, key.publicKey));
    const rejecting = google(() => passingVerdict(hash, { requestDetails: { requestPackageName: PKG, requestHash: hash, timestampMillis: String(at * 1000 - 1000) }, deviceIntegrity: {} }));
    const out = await attest(
      { platform: "android", challenge, publicKey: key.publicKey, integrityToken: "t", credential, signature: await key.sign(challenge) },
      await ctx(rejecting.fn, { nowMs: at * 1000 }),
    );
    expect(out).toMatchObject({ tier: "unattested", reason: "pi_device", transient: false });
  });
  it("rejects a credential from another install or a forged one", async () => {
    const { key, credential } = await registered();
    const challenge = await issueChallenge("sign", NOW);
    const other = await ctx(google(() => new Response("{}")).fn, { did: await deviceHash("another-install") });
    expect(await attest({ platform: "android", challenge, credential, signature: await key.sign(challenge) }, other)).toMatchObject({ reason: "cred_invalid", credentialStatus: "invalid" });
    expect(await attest({ platform: "android", challenge, credential: credential + "x", signature: await key.sign(challenge) }, await ctx(google(() => new Response("{}")).fn))).toMatchObject({ reason: "cred_invalid", credentialStatus: "invalid" });
  });
});

describe("ios registration and assertions", () => {
  async function registeredIos() {
    const store = memoryKeyStore();
    const leafKey = await newKey("P-256");
    const point = new Uint8Array((await crypto.subtle.exportKey("raw", leafKey.publicKey)) as ArrayBuffer);
    const keyId = btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest("SHA-256", point))));
    const challenge = await issueChallenge("sign", NOW);
    const fixture = await appAttestFixture({ appId: APP_ID, clientDataHash: await computeBinding(challenge, DEVICE, keyId), nowMs: NOW_MS, leafKey });
    const c = await ctx(google(() => new Response("{}")).fn, { keyStore: store, appleRoots: [fixture.root] });
    const out = await attest({ platform: "ios", challenge, keyId, attestation: b64url(fixture.attestation) }, c);
    return { store, leafKey, keyId, out, c, fixture, challenge };
  }

  it("registers the key once and issues a credential", async () => {
    const { out, keyId, c, fixture, challenge } = await registeredIos();
    expect(out).toMatchObject({ tier: "attested", reason: "ok" });
    expect(await verifyCredential("sign", out.credential!)).toMatchObject({ p: "i", k: keyId, env: "prod" });
    const again = await attest({ platform: "ios", challenge, keyId, attestation: b64url(fixture.attestation) }, c);
    expect(again).toMatchObject({ tier: "unattested", reason: "aa_exists", credentialStatus: "invalid" });
  });
  it("attests later sessions from assertions whose counter moves forward", async () => {
    const { leafKey, out, c } = await registeredIos();
    const credential = out.credential!;
    const keyId = (await verifyCredential("sign", credential))!.k;
    const session = async (counter: number) => {
      const challenge = await issueChallenge("sign", NOW);
      const assertion = await makeAssertion({ privateKey: leafKey.privateKey, appId: APP_ID, counter, clientDataHash: await computeBinding(challenge, DEVICE, keyId) });
      return attest({ platform: "ios", challenge, credential, assertion: b64url(assertion) }, c);
    };
    expect(await session(1)).toMatchObject({ tier: "attested", reason: "ok" });
    expect(await session(1)).toMatchObject({ tier: "unattested", reason: "aa_replay" });
    expect((await session(1)).credentialStatus).toBeUndefined();
    expect(await session(2)).toMatchObject({ tier: "attested" });
  });
  it("asks for re-registration when the store no longer knows the key", async () => {
    const { leafKey, out, c } = await registeredIos();
    const credential = out.credential!;
    const keyId = (await verifyCredential("sign", credential))!.k;
    const challenge = await issueChallenge("sign", NOW);
    const assertion = await makeAssertion({ privateKey: leafKey.privateKey, appId: APP_ID, counter: 1, clientDataHash: await computeBinding(challenge, DEVICE, keyId) });
    const out2 = await attest({ platform: "ios", challenge, credential, assertion: b64url(assertion) }, { ...c, keyStore: memoryKeyStore() });
    expect(out2).toMatchObject({ tier: "unattested", reason: "aa_unknown_key", credentialStatus: "invalid" });
  });
  it("asks for re-registration when the assertion is not signed by the credential's key", async () => {
    const { out, c } = await registeredIos();
    const credential = out.credential!;
    const keyId = (await verifyCredential("sign", credential))!.k;
    const challenge = await issueChallenge("sign", NOW);
    const stranger = await newKey("P-256");
    const assertion = await makeAssertion({ privateKey: stranger.privateKey, appId: APP_ID, counter: 1, clientDataHash: await computeBinding(challenge, DEVICE, keyId) });
    expect(await attest({ platform: "ios", challenge, credential, assertion: b64url(assertion) }, c)).toMatchObject({ tier: "unattested", reason: "sig_invalid", credentialStatus: "invalid" });
  });
  it("rejects a credential issued to another install", async () => {
    const { leafKey, out, c } = await registeredIos();
    const credential = out.credential!;
    const keyId = (await verifyCredential("sign", credential))!.k;
    const challenge = await issueChallenge("sign", NOW);
    const assertion = await makeAssertion({ privateKey: leafKey.privateKey, appId: APP_ID, counter: 1, clientDataHash: await computeBinding(challenge, DEVICE, keyId) });
    const other = { ...c, did: await deviceHash("another-install") };
    expect(await attest({ platform: "ios", challenge, credential, assertion: b64url(assertion) }, other)).toMatchObject({ tier: "unattested", reason: "cred_invalid", credentialStatus: "invalid" });
  });
  it("never reaches the store for an assertion that fails its signature", async () => {
    const { out, c } = await registeredIos();
    let touched = false;
    const spy: KeyStore = { register: async () => { touched = true; return "exists"; }, advance: async () => { touched = true; return "ok"; } };
    const challenge = await issueChallenge("sign", NOW);
    const stranger = await newKey("P-256");
    const assertion = await makeAssertion({ privateKey: stranger.privateKey, appId: APP_ID, counter: 1, clientDataHash: new Uint8Array(32) });
    const res = await attest({ platform: "ios", challenge, credential: out.credential!, assertion: b64url(assertion) }, { ...c, keyStore: spy });
    expect(res).toMatchObject({ tier: "unattested", reason: "sig_invalid" });
    expect(touched).toBe(false);
  });
});
