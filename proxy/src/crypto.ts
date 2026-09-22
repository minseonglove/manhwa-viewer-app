export type Bytes = Uint8Array<ArrayBuffer>;

const encoder = new TextEncoder();

export function utf8(text: string): Bytes {
  return encoder.encode(text) as Bytes;
}

export function concat(...parts: Uint8Array[]): Bytes {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}

export function b64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Standard or URL-safe base64, padded or not. */
export function fromBase64(text: string): Bytes {
  if (!/^[A-Za-z0-9+/_-]*={0,2}$/.test(text)) throw new Error("invalid base64");
  const bin = atob(text.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0)) as Bytes;
}

export async function sha256(data: Uint8Array): Promise<Bytes> {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", data)) as Bytes;
}

/** Constant time for equal lengths; a length mismatch returns at once, revealing only the length. */
export function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i]! ^ b[i]!;
  return diff === 0;
}

export async function equalSecrets(a: string, b: string): Promise<boolean> {
  const [x, y] = await Promise.all([sha256(utf8(a)), sha256(utf8(b))]);
  return equalBytes(x, y);
}

function importHmacKey(raw: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", raw, { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

export function hmacKey(secret: string): Promise<CryptoKey> {
  return importHmacKey(utf8(secret));
}

/** A key for one purpose, so a token minted for one purpose never verifies as another. */
export async function derivedHmacKey(secret: string, label: string): Promise<CryptoKey> {
  const raw = await crypto.subtle.sign("HMAC", await hmacKey(secret), utf8(label));
  return importHmacKey(new Uint8Array(raw));
}

export async function signToken(key: CryptoKey, payload: unknown): Promise<string> {
  const body = b64url(utf8(JSON.stringify(payload)));
  const sig = await crypto.subtle.sign("HMAC", key, utf8(body));
  return `${body}.${b64url(new Uint8Array(sig))}`;
}

/** The payload when the signature holds, else null. Callers still check its shape. */
export async function openToken(key: CryptoKey, token: string): Promise<unknown> {
  const [body, sig, ...rest] = token.split(".");
  if (!body || !sig || rest.length) return null;
  try {
    // subtle.verify compares in constant time; comparing signature strings would not.
    if (!(await crypto.subtle.verify("HMAC", key, fromBase64(sig), utf8(body)))) return null;
    return JSON.parse(new TextDecoder().decode(fromBase64(body)));
  } catch {
    return null;
  }
}

/** DER `SEQUENCE { INTEGER r, INTEGER s }` to the fixed-width `r ‖ s` that WebCrypto verifies. */
export function derSignatureToRaw(der: Uint8Array, size: number): Bytes {
  let pos = 0;
  const fail = (): never => {
    throw new Error("malformed ECDSA signature");
  };
  const readLength = (): number => {
    const first = der[pos++] ?? fail();
    if (first < 0x80) return first;
    if (first !== 0x81) return fail();
    return der[pos++] ?? fail();
  };
  if (der[pos++] !== 0x30) fail();
  if (readLength() !== der.length - pos) fail();
  const out = new Uint8Array(size * 2);
  for (const slot of [0, 1]) {
    if (der[pos++] !== 0x02) fail();
    const length = readLength();
    let integer = der.subarray(pos, pos + length);
    if (integer.length !== length) fail();
    pos += length;
    while (integer.length > 1 && integer[0] === 0) integer = integer.subarray(1);
    if (integer.length > size) fail();
    out.set(integer, slot * size + size - integer.length);
  }
  if (pos !== der.length) fail();
  return out as Bytes;
}
