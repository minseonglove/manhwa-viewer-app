import { APPLE_APP_ATTESTATION_ROOT } from "./attestation/apple-root";
import { verifyAppAssertion, verifyAppAttestation } from "./attestation/app-attest";
import { ANDROID_GRACE_SECONDS, ANDROID_RECHECK_SECONDS, issueCredential, verifyCredential } from "./attestation/credential";
import { parseServiceAccount, type GoogleAccessTokens } from "./attestation/google-oauth";
import type { KeyStore } from "./attestation/key-store-client";
import { decodeIntegrityToken, evaluateVerdict } from "./attestation/play-integrity";
import { computeBinding, verifyChallenge } from "./challenge";
import { b64url, derSignatureToRaw, equalSecrets, fromBase64, sha256, utf8 } from "./crypto";
import type { Env, Tier } from "./types";

const QUOTA_BACKOFF_MS = 60_000;
const CLOCK_SKEW_SECONDS = 60;

export interface AttestationContext {
  deviceId: string;
  did: string;
  env: Env;
  fetchFn: typeof fetch;
  keyStore: KeyStore | null;
  googleTokens: GoogleAccessTokens;
  backoff: { untilMs: number };
  nowMs: number;
  clientIp: string | null;
  appleRoots?: Uint8Array[];
}

export interface AttestationOutcome {
  tier: Tier;
  reason: string;
  /** The server, not the device, stood in the way; the session should be short so the device tries again soon. */
  transient: boolean;
  credential?: string;
  credentialStatus?: "invalid";
  recheckAfter?: number;
}

const unattested = (reason: string, extra: Partial<AttestationOutcome> = {}): AttestationOutcome => ({ tier: "unattested", reason, transient: false, ...extra });
const str = (v: unknown, max = 16_384): string | undefined => (typeof v === "string" && v.length > 0 && v.length <= max ? v : undefined);

export async function deviceHash(deviceId: string): Promise<string> {
  return [...(await sha256(utf8(deviceId))).slice(0, 12)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function attest(body: Record<string, unknown>, ctx: AttestationContext): Promise<AttestationOutcome> {
  if (body.platform === "dev") {
    // Exists so the whole chain can be exercised without platform attestation; production leaves the secret unset.
    const ok = !!ctx.env.DEV_ATTESTATION_SECRET && typeof body.secret === "string" && (await equalSecrets(body.secret, ctx.env.DEV_ATTESTATION_SECRET));
    return ok ? { tier: "attested", reason: "dev_ok", transient: false } : unattested("dev_denied");
  }
  if (body.platform !== "android" && body.platform !== "ios") return unattested("none");
  const challenge = str(body.challenge, 256);
  const nowSec = Math.floor(ctx.nowMs / 1000);
  if (!challenge || !(await verifyChallenge(ctx.env.SESSION_SIGNING_KEY, challenge, nowSec))) return unattested("challenge_invalid");
  return body.platform === "android" ? attestAndroid(body, challenge, nowSec, ctx) : attestIos(body, challenge, nowSec, ctx);
}

async function importP256Spki(spki: Uint8Array): Promise<CryptoKey | null> {
  try {
    return await crypto.subtle.importKey("spki", spki, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
  } catch {
    return null;
  }
}

async function verifyKeystoreSignature(publicKey: string, signature: string, binding: Uint8Array): Promise<boolean> {
  try {
    const key = await importP256Spki(fromBase64(publicKey));
    return !!key && (await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, derSignatureToRaw(fromBase64(signature), 32), binding));
  } catch {
    return false;
  }
}

type PlayIntegrityResult = { kind: "ok" } | { kind: "rejected"; reason: string } | { kind: "transient"; reason: string };

async function checkPlayIntegrity(integrityToken: string, requestHash: string, ctx: AttestationContext): Promise<PlayIntegrityResult> {
  if (ctx.backoff.untilMs > ctx.nowMs) return { kind: "transient", reason: "pi_backoff" };
  if (ctx.env.REGISTRATION_LIMITER && ctx.clientIp) {
    let limited = false;
    try {
      limited = !(await ctx.env.REGISTRATION_LIMITER.limit({ key: ctx.clientIp })).success;
    } catch {
      // Fails open: a broken limiter binding counts as no limiter rather than turning every registration into a 500.
    }
    if (limited) return { kind: "transient", reason: "pi_rate_limited" };
  }
  const sa = parseServiceAccount(ctx.env.PLAY_INTEGRITY_SERVICE_ACCOUNT);
  if (!sa) return { kind: "transient", reason: "pi_unconfigured" };
  let accessToken: string;
  try {
    accessToken = await ctx.googleTokens.get(sa, Math.floor(ctx.nowMs / 1000));
  } catch {
    return { kind: "transient", reason: "pi_auth" };
  }
  const decoded = await decodeIntegrityToken(ctx.fetchFn, accessToken, ctx.env.ANDROID_PACKAGE_NAME, integrityToken);
  if (decoded.kind === "invalid") return { kind: "rejected", reason: "pi_invalid_token" };
  if (decoded.kind === "transient") {
    if (decoded.quotaExceeded) ctx.backoff.untilMs = ctx.nowMs + QUOTA_BACKOFF_MS;
    if (decoded.accessTokenRejected) ctx.googleTokens.invalidate(accessToken);
    return { kind: "transient", reason: "pi_transient" };
  }
  const reason = evaluateVerdict(decoded.payload, { packageName: ctx.env.ANDROID_PACKAGE_NAME, requestHash, nowMs: ctx.nowMs });
  return reason === "ok" ? { kind: "ok" } : { kind: "rejected", reason };
}

async function attestAndroid(body: Record<string, unknown>, challenge: string, nowSec: number, ctx: AttestationContext): Promise<AttestationOutcome> {
  const publicKey = str(body.publicKey, 512);
  const integrityToken = str(body.integrityToken);
  const devSecret = str(body.devSecret, 256);
  const credential = str(body.credential, 2048);
  const signature = str(body.signature, 256);
  let transient = false;
  let reason = "none";

  if (publicKey && (integrityToken || devSecret)) {
    let spki: Uint8Array | null = null;
    try {
      spki = fromBase64(publicKey);
    } catch {
      spki = null;
    }
    if (!spki || !(await importP256Spki(spki))) return unattested("key_invalid");
    const issue = async (issuedReason: string): Promise<AttestationOutcome> => ({
      tier: "attested",
      reason: issuedReason,
      transient: false,
      credential: await issueCredential(ctx.env.SESSION_SIGNING_KEY, { p: "a", k: b64url(await sha256(spki!)), pk: b64url(spki!), d: ctx.did, va: nowSec }),
      recheckAfter: ANDROID_RECHECK_SECONDS,
    });
    if (devSecret) {
      const ok = !!ctx.env.DEV_ATTESTATION_SECRET && (await equalSecrets(devSecret, ctx.env.DEV_ATTESTATION_SECRET));
      return ok ? issue("dev_ok") : unattested("dev_denied");
    }
    const requestHash = b64url(await computeBinding(challenge, ctx.deviceId, publicKey));
    const result = await checkPlayIntegrity(integrityToken!, requestHash, ctx);
    if (result.kind === "ok") return issue("ok");
    if (result.kind === "rejected") return unattested(result.reason);
    transient = true;
    reason = result.reason;
  }

  if (credential && signature) {
    const cred = await verifyCredential(ctx.env.SESSION_SIGNING_KEY, credential);
    if (!cred || cred.p !== "a" || cred.d !== ctx.did) return unattested("cred_invalid", { transient, credentialStatus: "invalid" });
    const age = nowSec - cred.va;
    // A server-side failure during the recheck is not the device's fault; honour the credential a few days longer.
    if (age < -CLOCK_SKEW_SECONDS || age >= (transient ? ANDROID_GRACE_SECONDS : ANDROID_RECHECK_SECONDS)) {
      return unattested("cred_expired", { transient, credentialStatus: "invalid" });
    }
    if (!(await verifyKeystoreSignature(cred.pk, signature, await computeBinding(challenge, ctx.deviceId, cred.pk)))) {
      return unattested("sig_invalid", { transient, credentialStatus: "invalid" });
    }
    return { tier: "attested", reason: transient ? "grace" : "ok", transient: false, recheckAfter: Math.max(0, ANDROID_RECHECK_SECONDS - age) };
  }
  return unattested(reason, { transient });
}

async function attestIos(body: Record<string, unknown>, challenge: string, nowSec: number, ctx: AttestationContext): Promise<AttestationOutcome> {
  const keyStore = ctx.keyStore;
  if (!keyStore) return unattested("aa_unconfigured", { transient: true });
  const roots = ctx.appleRoots ?? [APPLE_APP_ATTESTATION_ROOT];
  const keyId = str(body.keyId, 64);
  const attestation = str(body.attestation);
  const credential = str(body.credential, 2048);
  const assertion = str(body.assertion, 4096);

  if (keyId && attestation) {
    let bytes: Uint8Array;
    try {
      bytes = fromBase64(attestation);
    } catch {
      return unattested("aa_format");
    }
    const verdict = await verifyAppAttestation({
      attestation: bytes,
      keyId,
      clientDataHash: await computeBinding(challenge, ctx.deviceId, keyId),
      appId: ctx.env.IOS_APP_ID,
      roots,
      nowMs: ctx.nowMs,
    });
    if (!verdict.ok) return unattested(verdict.reason);
    let registered;
    try {
      registered = await keyStore.register(keyId, verdict.env, ctx.did, nowSec);
    } catch {
      return unattested("aa_store", { transient: true });
    }
    if (registered === "exists") return unattested("aa_exists", { credentialStatus: "invalid" });
    return {
      tier: "attested",
      reason: "ok",
      transient: false,
      credential: await issueCredential(ctx.env.SESSION_SIGNING_KEY, { p: "i", k: keyId, pk: b64url(verdict.publicKey), d: ctx.did, env: verdict.env, va: nowSec }),
    };
  }

  if (credential && assertion) {
    const cred = await verifyCredential(ctx.env.SESSION_SIGNING_KEY, credential);
    if (!cred || cred.p !== "i" || cred.d !== ctx.did) return unattested("cred_invalid", { credentialStatus: "invalid" });
    let bytes: Uint8Array;
    try {
      bytes = fromBase64(assertion);
    } catch {
      return unattested("aa_format");
    }
    const verdict = await verifyAppAssertion({
      assertion: bytes,
      publicKey: fromBase64(cred.pk),
      clientDataHash: await computeBinding(challenge, ctx.deviceId, cred.k),
      appId: ctx.env.IOS_APP_ID,
    });
    if (!verdict.ok) return unattested(verdict.reason, verdict.reason === "sig_invalid" ? { credentialStatus: "invalid" } : {});
    let advanced;
    try {
      advanced = await keyStore.advance(cred.k, verdict.counter, ctx.did);
    } catch {
      return unattested("aa_store", { transient: true });
    }
    if (advanced === "ok") return { tier: "attested", reason: "ok", transient: false };
    if (advanced === "unknown") return unattested("aa_unknown_key", { credentialStatus: "invalid" });
    return unattested(advanced === "replay" ? "aa_replay" : "aa_device_mismatch");
  }
  return unattested("none");
}
