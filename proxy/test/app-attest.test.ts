import { decodeCBOR, encodeCBOR, type CBORType } from "@levischuck/tiny-cbor";
import { describe, expect, it } from "vitest";
import { APPLE_APP_ATTESTATION_ROOT } from "../src/attestation/apple-root";
import { verifyAppAssertion, verifyAppAttestation } from "../src/attestation/app-attest";
import { b64url, fromBase64, sha256, utf8 } from "../src/crypto";
import { APPLE_SAMPLE } from "./fixtures/apple-sample-attestation";
import { AAGUID, DAY, appAttestFixture, makeAssertion } from "./helpers/pki";

const APP_ID = "PC497J52FF.com.manhwaviewer.app";
const NOW = Date.UTC(2026, 8, 22);

describe("verifyAppAttestation — Apple's own sample", () => {
  const input = {
    attestation: fromBase64(APPLE_SAMPLE.attestation),
    keyId: APPLE_SAMPLE.keyId,
    clientDataHash: utf8(APPLE_SAMPLE.clientData),
    appId: APPLE_SAMPLE.appId,
    roots: [APPLE_APP_ATTESTATION_ROOT],
    nowMs: APPLE_SAMPLE.validAtMs,
  };
  it("verifies against the real Apple root while the sample's leaf is valid", async () => {
    const verdict = await verifyAppAttestation(input);
    expect(verdict.ok && verdict.env).toBe("prod");
    expect(verdict.ok && b64url(await sha256(verdict.publicKey))).toBe(b64url(fromBase64(APPLE_SAMPLE.keyId)));
  });
  it("fails once the leaf has expired, for our App ID, and with other client data", async () => {
    expect(await verifyAppAttestation({ ...input, nowMs: Date.UTC(2026, 4, 1) })).toEqual({ ok: false, reason: "aa_chain" });
    expect(await verifyAppAttestation({ ...input, appId: APP_ID })).toEqual({ ok: false, reason: "aa_rp" });
    expect(await verifyAppAttestation({ ...input, clientDataHash: utf8("other") })).toEqual({ ok: false, reason: "aa_nonce" });
  });
});

describe("verifyAppAttestation — test CA", () => {
  const clientDataHash = new Uint8Array(32).fill(9);
  const verify = async (fixture: Awaited<ReturnType<typeof appAttestFixture>>, overrides: Record<string, unknown> = {}) =>
    verifyAppAttestation({ attestation: fixture.attestation, keyId: fixture.keyId, clientDataHash, appId: APP_ID, roots: [fixture.root], nowMs: NOW, ...overrides });

  it("accepts production and development keys and reports which", async () => {
    const prod = await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW });
    const verdict = await verify(prod);
    expect(verdict.ok && verdict.env).toBe("prod");
    expect(verdict.ok && [...verdict.publicKey]).toEqual([...prod.point]);
    const dev = await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW, aaguid: AAGUID.dev });
    const devVerdict = await verify(dev);
    expect(devVerdict.ok && devVerdict.env).toBe("dev");
  });
  it("rejects each broken property with its reason", async () => {
    const good = await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW });
    expect(await verify(await appAttestFixture({ appId: APP_ID, rpAppId: "PC497J52FF.com.other", clientDataHash, nowMs: NOW }))).toEqual({ ok: false, reason: "aa_rp" });
    expect(await verify(await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW, counter: 1 }))).toEqual({ ok: false, reason: "aa_counter" });
    expect(await verify(await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW, aaguid: new Uint8Array(16) }))).toEqual({ ok: false, reason: "aa_env" });
    expect(await verify(await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW, wrongNonce: true }))).toEqual({ ok: false, reason: "aa_nonce" });
    expect(await verify(good, { keyId: btoa("x".repeat(32)) })).toEqual({ ok: false, reason: "aa_key" });
    expect(await verify(good, { keyId: "not base64!" })).toEqual({ ok: false, reason: "aa_key" });
    expect(await verify(good, { roots: [APPLE_APP_ATTESTATION_ROOT] })).toEqual({ ok: false, reason: "aa_chain" });
    expect(await verify(good, { nowMs: NOW + 3 * DAY })).toEqual({ ok: false, reason: "aa_chain" });
    expect(await verify(good, { attestation: good.attestation.subarray(0, 100) })).toEqual({ ok: false, reason: "aa_format" });
    expect(await verify(good, { attestation: utf8("garbage") })).toEqual({ ok: false, reason: "aa_format" });
  });
  it("rejects a certificate signature altered in transit", async () => {
    const good = await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW });
    const at = good.attestation.length - 400;
    const broken = new Uint8Array(good.attestation);
    broken[at]! ^= 0x01;
    const verdict = await verify(good, { attestation: broken });
    expect(verdict.ok).toBe(false);
  });
  it("rejects a keyId spelled differently from its canonical padded base64", async () => {
    // The Durable Object is named by the keyId string, so an alternative spelling of the same key would get a separate counter.
    const good = await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW });
    const urlSafeUnpadded = good.keyId.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    expect(await verify(good, { keyId: urlSafeUnpadded })).toEqual({ ok: false, reason: "aa_key" });
  });
  it("rejects a certificate chain that carries more than leaf and intermediate", async () => {
    const good = await appAttestFixture({ appId: APP_ID, clientDataHash, nowMs: NOW });
    const object = decodeCBOR(good.attestation) as Map<string, CBORType>;
    const attStmt = object.get("attStmt") as Map<string, CBORType>;
    const x5c = attStmt.get("x5c") as Uint8Array[];
    attStmt.set("x5c", [...x5c, good.root]);
    expect(await verify(good, { attestation: encodeCBOR(object) })).toEqual({ ok: false, reason: "aa_format" });
  });
});

describe("verifyAppAssertion", () => {
  it("accepts a signature from the attested key and returns its counter", async () => {
    const f = await appAttestFixture({ appId: APP_ID, clientDataHash: new Uint8Array(32), nowMs: NOW });
    const cdh = new Uint8Array(32).fill(3);
    const assertion = await makeAssertion({ privateKey: f.leafKey.privateKey, appId: APP_ID, counter: 7, clientDataHash: cdh });
    expect(await verifyAppAssertion({ assertion, publicKey: f.point, clientDataHash: cdh, appId: APP_ID })).toEqual({ ok: true, counter: 7 });
  });
  it("rejects other client data, another key, another app and garbage", async () => {
    const f = await appAttestFixture({ appId: APP_ID, clientDataHash: new Uint8Array(32), nowMs: NOW });
    const other = await appAttestFixture({ appId: APP_ID, clientDataHash: new Uint8Array(32), nowMs: NOW });
    const cdh = new Uint8Array(32).fill(3);
    const assertion = await makeAssertion({ privateKey: f.leafKey.privateKey, appId: APP_ID, counter: 1, clientDataHash: cdh });
    expect(await verifyAppAssertion({ assertion, publicKey: f.point, clientDataHash: new Uint8Array(32), appId: APP_ID })).toEqual({ ok: false, reason: "sig_invalid" });
    expect(await verifyAppAssertion({ assertion, publicKey: other.point, clientDataHash: cdh, appId: APP_ID })).toEqual({ ok: false, reason: "sig_invalid" });
    const otherApp = await makeAssertion({ privateKey: f.leafKey.privateKey, appId: "X.com.other", counter: 1, clientDataHash: cdh });
    expect(await verifyAppAssertion({ assertion: otherApp, publicKey: f.point, clientDataHash: cdh, appId: APP_ID })).toEqual({ ok: false, reason: "aa_rp" });
    expect(await verifyAppAssertion({ assertion: utf8("x"), publicKey: f.point, clientDataHash: cdh, appId: APP_ID })).toEqual({ ok: false, reason: "aa_format" });
  });
});
