import { b64url, concat, derivedHmacKey, openToken, sha256, signToken, utf8, type Bytes } from "./crypto";

export const CHALLENGE_TTL_SECONDS = 300;
const MAX_CHALLENGE_LENGTH = 256;
const LABEL = "challenge-v1";

interface ChallengePayload {
  t: "c";
  e: number;
  n: string;
}

export async function issueChallenge(secret: string, nowSec: number): Promise<string> {
  const payload: ChallengePayload = { t: "c", e: nowSec + CHALLENGE_TTL_SECONDS, n: b64url(crypto.getRandomValues(new Uint8Array(16))) };
  return signToken(await derivedHmacKey(secret, LABEL), payload);
}

export async function verifyChallenge(secret: string, challenge: string, nowSec: number): Promise<boolean> {
  if (challenge.length > MAX_CHALLENGE_LENGTH) return false;
  const payload = (await openToken(await derivedHmacKey(secret, LABEL), challenge)) as Partial<ChallengePayload> | null;
  return payload?.t === "c" && typeof payload.e === "number" && payload.e > nowSec && typeof payload.n === "string";
}

const SEPARATOR = new Uint8Array([0]);

/** What the device signs, or has Google or Apple vouch for: this challenge, this install, this key. */
export function computeBinding(challenge: string, deviceId: string, key: string): Promise<Bytes> {
  return sha256(concat(utf8("mv-session-v1"), SEPARATOR, utf8(challenge), SEPARATOR, utf8(deviceId), SEPARATOR, utf8(key)));
}
