import type { Tier } from "./types";

export interface SessionPayload {
  did: string;
  tier: Tier;
  exp: number;
}

const encoder = new TextEncoder();

function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(s: string): Uint8Array {
  const bin = atob(s.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

export async function issueSessionToken(secret: string, claims: { did: string; tier: Tier }, nowSec: number, ttlSec: number): Promise<string> {
  const body = b64url(encoder.encode(JSON.stringify({ did: claims.did, tier: claims.tier, exp: nowSec + ttlSec } satisfies SessionPayload)));
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), encoder.encode(body));
  return `${body}.${b64url(new Uint8Array(sig))}`;
}

export async function verifySessionToken(secret: string, token: string, nowSec: number): Promise<SessionPayload | null> {
  const [body, sig, ...rest] = token.split(".");
  if (!body || !sig || rest.length) return null;
  try {
    // subtle.verify compares in constant time; comparing signature strings would not.
    if (!(await crypto.subtle.verify("HMAC", await hmacKey(secret), fromB64url(sig), encoder.encode(body)))) return null;
    const payload = JSON.parse(new TextDecoder().decode(fromB64url(body))) as SessionPayload;
    if (typeof payload.did !== "string" || (payload.tier !== "attested" && payload.tier !== "unattested")) return null;
    return typeof payload.exp === "number" && payload.exp > nowSec ? payload : null;
  } catch {
    return null;
  }
}
