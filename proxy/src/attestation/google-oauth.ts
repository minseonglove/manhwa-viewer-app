import { b64url, fromBase64, utf8 } from "../crypto";

export const PLAY_INTEGRITY_SCOPE = "https://www.googleapis.com/auth/playintegrity";
export const GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
const ASSERTION_LIFETIME_SECONDS = 3600;
const REFRESH_MARGIN_SECONDS = 300;
const TOKEN_TIMEOUT_MS = 5_000;

export interface ServiceAccount {
  client_email: string;
  private_key: string;
}

export function parseServiceAccount(json: string | undefined): ServiceAccount | null {
  if (!json) return null;
  try {
    const sa = JSON.parse(json) as Partial<ServiceAccount>;
    return typeof sa.client_email === "string" && typeof sa.private_key === "string" ? { client_email: sa.client_email, private_key: sa.private_key } : null;
  } catch {
    return null;
  }
}

function pkcs8FromPem(pem: string) {
  return fromBase64(pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, ""));
}

export async function signServiceAccountJwt(sa: ServiceAccount, nowSec: number): Promise<string> {
  const key = await crypto.subtle.importKey("pkcs8", pkcs8FromPem(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const header = b64url(utf8(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = b64url(utf8(JSON.stringify({ iss: sa.client_email, scope: PLAY_INTEGRITY_SCOPE, aud: GOOGLE_TOKEN_URL, iat: nowSec, exp: nowSec + ASSERTION_LIFETIME_SECONDS })));
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, utf8(`${header}.${claims}`));
  return `${header}.${claims}.${b64url(new Uint8Array(signature))}`;
}

/** Access tokens for the Play Integrity service account, reused until shortly before expiry. One instance lives per isolate. */
export class GoogleAccessTokens {
  private cached: { email: string; token: string; expiresAtSec: number } | null = null;

  constructor(
    private readonly fetchFn: typeof fetch,
    private readonly timeoutMs = TOKEN_TIMEOUT_MS,
  ) {}

  async get(sa: ServiceAccount, nowSec: number): Promise<string> {
    const cached = this.cached;
    if (cached && cached.email === sa.client_email && cached.expiresAtSec - REFRESH_MARGIN_SECONDS > nowSec) return cached.token;
    let assertion: string;
    try {
      assertion = await signServiceAccountJwt(sa, nowSec);
    } catch {
      throw new Error("oauth key invalid");
    }
    // Called through a local binding, never as `this.fetchFn(...)`: workerd's global fetch throws "Illegal invocation"
    // when invoked as a method of another object, unlike Node's fetch, which does not check its receiver.
    const fetchFn = this.fetchFn;
    const signal = AbortSignal.timeout(this.timeoutMs);
    let response: Response;
    try {
      response = await fetchFn(GOOGLE_TOKEN_URL, {
        method: "POST",
        signal,
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
      });
    } catch {
      throw new Error(signal.aborted ? "oauth timeout" : "oauth request failed");
    }
    if (!response.ok) throw new Error(`oauth HTTP ${response.status}`);
    const body = (await response.json().catch(() => ({}))) as { access_token?: unknown; expires_in?: unknown };
    if (typeof body.access_token !== "string" || typeof body.expires_in !== "number") throw new Error("oauth response malformed");
    this.cached = { email: sa.client_email, token: body.access_token, expiresAtSec: nowSec + body.expires_in };
    return body.access_token;
  }

  /** Forgets `token` so the next `get` mints a new one; a token that has already been replaced is left alone. */
  invalidate(token: string): void {
    if (this.cached?.token === token) this.cached = null;
  }
}
