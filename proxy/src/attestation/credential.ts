import { derivedHmacKey, openToken, signToken } from "../crypto";

export const ANDROID_RECHECK_SECONDS = 7 * 24 * 60 * 60;
export const ANDROID_GRACE_SECONDS = 10 * 24 * 60 * 60;
const LABEL = "credential-v1";
const MAX_CREDENTIAL_LENGTH = 2048;

/** A device key the proxy has verified once: platform, key id, public key, install hash, App Attest environment, verification time. */
export interface Credential {
  v: 1;
  p: "a" | "i";
  k: string;
  pk: string;
  d: string;
  env?: "dev" | "prod";
  va: number;
}

export type CredentialClaims = Omit<Credential, "v">;

export async function issueCredential(secret: string, claims: CredentialClaims): Promise<string> {
  return signToken(await derivedHmacKey(secret, LABEL), { v: 1, ...claims } satisfies Credential);
}

export async function verifyCredential(secret: string, token: string): Promise<Credential | null> {
  if (token.length > MAX_CREDENTIAL_LENGTH) return null;
  const c = (await openToken(await derivedHmacKey(secret, LABEL), token)) as Partial<Credential> | null;
  if (!c || c.v !== 1 || (c.p !== "a" && c.p !== "i")) return null;
  if (typeof c.k !== "string" || typeof c.pk !== "string" || typeof c.d !== "string" || typeof c.va !== "number") return null;
  if (c.p === "i" && c.env !== "dev" && c.env !== "prod") return null;
  return c as Credential;
}
