import { derSignatureToRaw } from "../crypto";

export class DerError extends Error {}

export interface Tlv {
  tag: number;
  value: Uint8Array;
  raw: Uint8Array;
  end: number;
}

function fail(): never {
  throw new DerError("malformed DER");
}

export function readTlv(buf: Uint8Array, offset: number): Tlv {
  let pos = offset;
  const tag = buf[pos++] ?? fail();
  // High-tag-number form never appears in the certificates this reads.
  if ((tag & 0x1f) === 0x1f) fail();
  const first = buf[pos++] ?? fail();
  let length = first;
  if (first & 0x80) {
    const count = first & 0x7f;
    // Zero means indefinite length, which DER forbids.
    if (count === 0 || count > 3) fail();
    length = 0;
    for (let i = 0; i < count; i++) length = length * 256 + (buf[pos++] ?? fail());
  }
  const end = pos + length;
  if (end > buf.length) fail();
  return { tag, value: buf.subarray(pos, end), raw: buf.subarray(offset, end), end };
}

export function readChildren(value: Uint8Array): Tlv[] {
  const out: Tlv[] = [];
  for (let pos = 0; pos < value.length; ) {
    const child = readTlv(value, pos);
    out.push(child);
    pos = child.end;
  }
  return out;
}

export function readOid(value: Uint8Array): string {
  const first = value[0] ?? fail();
  const parts = [Math.floor(first / 40), first % 40];
  let n = 0;
  for (let i = 1; i < value.length; i++) {
    n = n * 128 + (value[i]! & 0x7f);
    if (!(value[i]! & 0x80)) {
      parts.push(n);
      n = 0;
    }
  }
  return parts.join(".");
}

export const OID = {
  ecPublicKey: "1.2.840.10045.2.1",
  p256: "1.2.840.10045.3.1.7",
  p384: "1.3.132.0.34",
  ecdsaSha256: "1.2.840.10045.4.3.2",
  ecdsaSha384: "1.2.840.10045.4.3.3",
} as const;

export interface Certificate {
  tbs: Uint8Array;
  signatureAlgorithm: string;
  signature: Uint8Array;
  notBeforeMs: number;
  notAfterMs: number;
  spki: Uint8Array;
  curve: string;
  publicKey: Uint8Array;
  extensions: Map<string, Uint8Array>;
}

const need = <T>(value: T | undefined, tag?: number): T & Tlv => {
  if (!value || (tag !== undefined && (value as unknown as Tlv).tag !== tag)) fail();
  return value as T & Tlv;
};

function readTime(tlv: Tlv): number {
  const text = new TextDecoder().decode(tlv.value);
  const m =
    tlv.tag === 0x17 ? /^(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/.exec(text) : tlv.tag === 0x18 ? /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})Z$/.exec(text) : null;
  if (!m) fail();
  let year = Number(m[1]);
  if (tlv.tag === 0x17) year += year >= 50 ? 1900 : 2000;
  const month = Number(m[2]);
  const day = Number(m[3]);
  const hour = Number(m[4]);
  const minute = Number(m[5]);
  const second = Number(m[6]);
  const ms = Date.UTC(year, month - 1, day, hour, minute, second);
  // Date.UTC silently rolls out-of-range fields (month 13, hour 24, Feb 30, …) into another date;
  // reject anything that doesn't round-trip back to the exact fields that were parsed.
  const d = new Date(ms);
  if (
    d.getUTCFullYear() !== year ||
    d.getUTCMonth() !== month - 1 ||
    d.getUTCDate() !== day ||
    d.getUTCHours() !== hour ||
    d.getUTCMinutes() !== minute ||
    d.getUTCSeconds() !== second
  )
    fail();
  return ms;
}

export function parseCertificate(der: Uint8Array): Certificate {
  try {
    const outer = readTlv(der, 0);
    if (outer.tag !== 0x30 || outer.end !== der.length) fail();
    const [tbsTlv, sigAlg, sigValue] = readChildren(outer.value);
    need(tbsTlv, 0x30);
    need(sigAlg, 0x30);
    need(sigValue, 0x03);
    const all = readChildren(tbsTlv!.value);
    const fields = all[0]?.tag === 0xa0 ? all.slice(1) : all;
    const validity = need(fields[3], 0x30);
    const spkiTlv = need(fields[5], 0x30);
    const [notBefore, notAfter] = readChildren(validity.value);
    const [algorithm, key] = readChildren(spkiTlv.value);
    const [algOid, curveOid] = readChildren(need(algorithm, 0x30).value);
    if (readOid(need(algOid, 0x06).value) !== OID.ecPublicKey) fail();
    const keyBits = need(key, 0x03).value;
    if (keyBits[0] !== 0) fail();
    const extensions = new Map<string, Uint8Array>();
    const extField = fields.slice(6).find((f) => f.tag === 0xa3);
    if (extField) {
      for (const ext of readChildren(need(readChildren(extField.value)[0], 0x30).value)) {
        const parts = readChildren(need(ext, 0x30).value);
        extensions.set(readOid(need(parts[0], 0x06).value), need(parts[parts.length - 1], 0x04).value);
      }
    }
    const sigBits = sigValue!.value;
    if (sigBits[0] !== 0) fail();
    return {
      tbs: tbsTlv!.raw,
      signatureAlgorithm: readOid(need(readChildren(sigAlg!.value)[0], 0x06).value),
      signature: sigBits.subarray(1),
      notBeforeMs: readTime(need(notBefore)),
      notAfterMs: readTime(need(notAfter)),
      spki: spkiTlv.raw,
      curve: readOid(need(curveOid, 0x06).value),
      publicKey: keyBits.subarray(1),
      extensions,
    };
  } catch (e) {
    if (e instanceof DerError) throw e;
    throw new DerError("malformed certificate");
  }
}

const CURVES: Record<string, { name: "P-256" | "P-384"; size: number }> = {
  [OID.p256]: { name: "P-256", size: 32 },
  [OID.p384]: { name: "P-384", size: 48 },
};
const HASHES: Record<string, "SHA-256" | "SHA-384"> = { [OID.ecdsaSha256]: "SHA-256", [OID.ecdsaSha384]: "SHA-384" };

export async function isSignedBy(cert: Certificate, issuer: Certificate): Promise<boolean> {
  const curve = CURVES[issuer.curve];
  const hash = HASHES[cert.signatureAlgorithm];
  if (!curve || !hash) return false;
  try {
    const key = await crypto.subtle.importKey("spki", issuer.spki, { name: "ECDSA", namedCurve: curve.name }, false, ["verify"]);
    return await crypto.subtle.verify({ name: "ECDSA", hash }, key, derSignatureToRaw(cert.signature, curve.size), cert.tbs);
  } catch {
    return false;
  }
}

/** chain[0] is the leaf. Each certificate must be signed by the next, the last by a pinned root, and all must be valid at nowMs. */
export async function verifyChain(chain: Certificate[], roots: Certificate[], nowMs: number): Promise<boolean> {
  if (chain.length === 0) return false;
  const valid = (c: Certificate) => c.notBeforeMs <= nowMs && nowMs <= c.notAfterMs;
  if (!chain.every(valid)) return false;
  for (let i = 0; i + 1 < chain.length; i++) if (!(await isSignedBy(chain[i]!, chain[i + 1]!))) return false;
  const top = chain[chain.length - 1]!;
  for (const root of roots) if (valid(root) && (await isSignedBy(top, root))) return true;
  return false;
}
