import { hmacKey, openToken, signToken } from "./crypto";
import type { Tier } from "./types";

export interface SessionPayload {
  did: string;
  tier: Tier;
  exp: number;
}

export async function issueSessionToken(secret: string, claims: { did: string; tier: Tier }, nowSec: number, ttlSec: number): Promise<string> {
  return signToken(await hmacKey(secret), { did: claims.did, tier: claims.tier, exp: nowSec + ttlSec } satisfies SessionPayload);
}

export async function verifySessionToken(secret: string, token: string, nowSec: number): Promise<SessionPayload | null> {
  const payload = (await openToken(await hmacKey(secret), token)) as Partial<SessionPayload> | null;
  if (!payload || typeof payload.did !== "string" || (payload.tier !== "attested" && payload.tier !== "unattested")) return null;
  return typeof payload.exp === "number" && payload.exp > nowSec ? (payload as SessionPayload) : null;
}
