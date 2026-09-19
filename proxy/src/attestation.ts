import type { Env } from "./types";

async function sha256(text: string): Promise<Uint8Array> {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
}

async function constantTimeEquals(a: string, b: string): Promise<boolean> {
  const [x, y] = await Promise.all([sha256(a), sha256(b)]);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i]! ^ y[i]!;
  return diff === 0;
}

/**
 * True when the request proves it comes from a genuine install.
 * Play Integrity and App Attest are not wired in yet, so real platforms verify as false and get the unattested tier.
 */
export async function verifyAttestation(body: Record<string, unknown>, env: Env): Promise<boolean> {
  if (body.platform === "dev") {
    // Exists so the whole chain can be exercised before the platform verifiers; production leaves the secret unset.
    if (!env.DEV_ATTESTATION_SECRET || typeof body.secret !== "string") return false;
    return constantTimeEquals(body.secret, env.DEV_ATTESTATION_SECRET);
  }
  return false;
}

export async function deviceHash(deviceId: string): Promise<string> {
  return [...(await sha256(deviceId)).slice(0, 12)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
