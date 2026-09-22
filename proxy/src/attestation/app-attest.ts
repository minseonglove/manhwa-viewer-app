import { decodeCBOR, type CBORType } from "@levischuck/tiny-cbor";
import { concat, derSignatureToRaw, equalBytes, fromBase64, sha256, utf8, type Bytes } from "../crypto";
import { OID, parseCertificate, readChildren, readTlv, verifyChain, type Certificate } from "./der";

export const NONCE_EXTENSION_OID = "1.2.840.113635.100.8.2";
const AAGUID_DEVELOPMENT = utf8("appattestdevelop");
const AAGUID_PRODUCTION = concat(utf8("appattest"), new Uint8Array(7));

export type AppAttestEnvironment = "dev" | "prod";
type AttestationFailure = "aa_format" | "aa_rp" | "aa_counter" | "aa_env" | "aa_key" | "aa_chain" | "aa_nonce";
export type AttestationVerdict = { ok: true; publicKey: Bytes; env: AppAttestEnvironment } | { ok: false; reason: AttestationFailure };
export type AssertionVerdict = { ok: true; counter: number } | { ok: false; reason: "aa_format" | "sig_invalid" | "aa_rp" };

const field = (value: CBORType | undefined, key: string): CBORType | undefined => (value instanceof Map ? value.get(key) : undefined);

function decode(bytes: Uint8Array): CBORType | undefined {
  try {
    return decodeCBOR(new Uint8Array(bytes));
  } catch {
    return undefined;
  }
}

/** The nonce sits in `SEQUENCE { [1] EXPLICIT OCTET STRING }`; Apple's text mentions only the OCTET STRING. */
function readNonce(value: Uint8Array | undefined): Uint8Array | null {
  if (!value) return null;
  try {
    const seq = readTlv(value, 0);
    const tagged = seq.tag === 0x30 ? readChildren(seq.value).find((t) => t.tag === 0xa1) : undefined;
    const octet = tagged ? readTlv(tagged.value, 0) : undefined;
    return octet?.tag === 0x04 ? octet.value : null;
  } catch {
    return null;
  }
}

export async function verifyAppAttestation(input: {
  attestation: Uint8Array;
  keyId: string;
  clientDataHash: Uint8Array;
  appId: string;
  roots: Uint8Array[];
  nowMs: number;
}): Promise<AttestationVerdict> {
  const fail = (reason: AttestationFailure): AttestationVerdict => ({ ok: false, reason });
  const object = decode(input.attestation);
  const authData = field(object, "authData");
  const x5c = field(field(object, "attStmt"), "x5c");
  if (field(object, "fmt") !== "apple-appattest" || !(authData instanceof Uint8Array) || !Array.isArray(x5c) || x5c.length !== 2) return fail("aa_format");
  if (!x5c.every((c): c is Uint8Array => c instanceof Uint8Array)) return fail("aa_format");
  if (authData.length < 55) return fail("aa_format");
  const view = new DataView(authData.buffer, authData.byteOffset, authData.byteLength);
  const credentialId = authData.subarray(55, 55 + view.getUint16(53));
  if (credentialId.length !== view.getUint16(53)) return fail("aa_format");

  if (!equalBytes(authData.subarray(0, 32), await sha256(utf8(input.appId)))) return fail("aa_rp");
  if (view.getUint32(33) !== 0) return fail("aa_counter");
  const aaguid = authData.subarray(37, 53);
  const env: AppAttestEnvironment | null = equalBytes(aaguid, AAGUID_PRODUCTION) ? "prod" : equalBytes(aaguid, AAGUID_DEVELOPMENT) ? "dev" : null;
  if (!env) return fail("aa_env");
  let keyIdBytes: Uint8Array;
  try {
    keyIdBytes = fromBase64(input.keyId);
  } catch {
    return fail("aa_key");
  }
  // Padded standard base64 only: the Durable Object is named by this string, so another spelling of the same bytes would get a separate counter.
  if (btoa(String.fromCharCode(...keyIdBytes)) !== input.keyId) return fail("aa_key");
  if (keyIdBytes.length !== 32 || !equalBytes(credentialId, keyIdBytes)) return fail("aa_key");

  let chain: Certificate[];
  let roots: Certificate[];
  try {
    chain = x5c.map(parseCertificate);
    roots = input.roots.map(parseCertificate);
  } catch {
    return fail("aa_format");
  }
  const leaf = chain[0]!;
  if (leaf.curve !== OID.p256 || leaf.publicKey.length !== 65 || leaf.publicKey[0] !== 0x04) return fail("aa_chain");
  if (!(await verifyChain(chain, roots, input.nowMs))) return fail("aa_chain");

  const nonce = await sha256(concat(authData, input.clientDataHash));
  const embedded = readNonce(leaf.extensions.get(NONCE_EXTENSION_OID));
  if (!embedded || !equalBytes(embedded, nonce)) return fail("aa_nonce");
  if (!equalBytes(await sha256(leaf.publicKey), keyIdBytes)) return fail("aa_key");
  return { ok: true, publicKey: new Uint8Array(leaf.publicKey), env };
}

export async function verifyAppAssertion(input: { assertion: Uint8Array; publicKey: Uint8Array; clientDataHash: Uint8Array; appId: string }): Promise<AssertionVerdict> {
  const object = decode(input.assertion);
  const signature = field(object, "signature");
  const authenticatorData = field(object, "authenticatorData");
  if (!(signature instanceof Uint8Array) || !(authenticatorData instanceof Uint8Array) || authenticatorData.length < 37) return { ok: false, reason: "aa_format" };
  const nonce = await sha256(concat(authenticatorData, input.clientDataHash));
  try {
    const key = await crypto.subtle.importKey("raw", input.publicKey, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    // Apple says the signature is "valid for the nonce"; that is ECDSA with SHA-256 over the nonce bytes, so the nonce is hashed once more here.
    if (!(await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, derSignatureToRaw(signature, 32), nonce))) return { ok: false, reason: "sig_invalid" };
  } catch {
    return { ok: false, reason: "sig_invalid" };
  }
  if (!equalBytes(authenticatorData.subarray(0, 32), await sha256(utf8(input.appId)))) return { ok: false, reason: "aa_rp" };
  return { ok: true, counter: new DataView(authenticatorData.buffer, authenticatorData.byteOffset, authenticatorData.byteLength).getUint32(33) };
}
