export const REQUEST_FRESHNESS_MS = 5 * 60_000;
export const CLOCK_SKEW_MS = 60_000;
const DECODE_TIMEOUT_MS = 5_000;

export type DecodeOutcome =
  | { kind: "payload"; payload: unknown }
  | { kind: "invalid" }
  | { kind: "transient"; quotaExceeded: boolean; accessTokenRejected: boolean };

/** Google decodes standard tokens only on its own servers. A 400 means the token itself is bad; anything else is Google's or our problem. */
export async function decodeIntegrityToken(
  fetchFn: typeof fetch,
  accessToken: string,
  packageName: string,
  integrityToken: string,
  timeoutMs = DECODE_TIMEOUT_MS,
): Promise<DecodeOutcome> {
  const transient: DecodeOutcome = { kind: "transient", quotaExceeded: false, accessTokenRejected: false };
  let response: Response;
  try {
    response = await fetchFn(`https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`, {
      method: "POST",
      signal: AbortSignal.timeout(timeoutMs),
      headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ integrityToken }),
    });
  } catch {
    return transient;
  }
  if (response.status === 400) return { kind: "invalid" };
  if (response.status === 429) return { ...transient, quotaExceeded: true };
  if (response.status === 401 || response.status === 403) return { ...transient, accessTokenRejected: true };
  if (!response.ok) return transient;
  try {
    const body = (await response.json()) as { tokenPayloadExternal?: unknown };
    return body.tokenPayloadExternal ? { kind: "payload", payload: body.tokenPayloadExternal } : transient;
  } catch {
    return transient;
  }
}

export type VerdictReason = "ok" | "pi_package" | "pi_hash" | "pi_stale" | "pi_app" | "pi_device" | "pi_license";

interface VerdictPayload {
  requestDetails?: { requestPackageName?: unknown; requestHash?: unknown; timestampMillis?: unknown };
  appIntegrity?: { appRecognitionVerdict?: unknown; packageName?: unknown };
  deviceIntegrity?: { deviceRecognitionVerdict?: unknown };
  accountDetails?: { appLicensingVerdict?: unknown };
}

export function evaluateVerdict(payload: unknown, expected: { packageName: string; requestHash: string; nowMs: number }): VerdictReason {
  const p = (payload && typeof payload === "object" ? payload : {}) as VerdictPayload;
  const request = p.requestDetails ?? {};
  if (request.requestPackageName !== expected.packageName) return "pi_package";
  if (request.requestHash !== expected.requestHash) return "pi_hash";
  const issuedMs = typeof request.timestampMillis === "string" || typeof request.timestampMillis === "number" ? Number(request.timestampMillis) : NaN;
  if (!Number.isFinite(issuedMs) || issuedMs < expected.nowMs - REQUEST_FRESHNESS_MS || issuedMs > expected.nowMs + CLOCK_SKEW_MS) return "pi_stale";
  if (p.appIntegrity?.appRecognitionVerdict !== "PLAY_RECOGNIZED" || p.appIntegrity.packageName !== expected.packageName) return "pi_app";
  const device = p.deviceIntegrity?.deviceRecognitionVerdict;
  if (!Array.isArray(device) || !device.includes("MEETS_DEVICE_INTEGRITY")) return "pi_device";
  if (p.accountDetails?.appLicensingVerdict !== "LICENSED") return "pi_license";
  return "ok";
}
