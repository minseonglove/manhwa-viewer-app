import { encodeCBOR, type CBORType } from "@levischuck/tiny-cbor";
import { concat, sha256, utf8 } from "../../src/crypto";
import { bitString, explicit, int, octet, oid, rawSignatureToDer, seq, set, utcTime, utf8String } from "./der";

export type Curve = "P-256" | "P-384" | "P-521";
export const DAY = 86_400_000;

const SIGNATURE_OIDS: Record<"SHA-256" | "SHA-384" | "SHA-512", string> = {
  "SHA-256": "1.2.840.10045.4.3.2",
  "SHA-384": "1.2.840.10045.4.3.3",
  "SHA-512": "1.2.840.10045.4.3.4",
};

export async function newKey(curve: Curve): Promise<CryptoKeyPair> {
  return (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: curve }, true, ["sign", "verify"])) as CryptoKeyPair;
}

export async function makeCertificate(opts: {
  subject: string;
  issuer: string;
  subjectKey: CryptoKey;
  issuerKey: CryptoKey;
  hash: "SHA-256" | "SHA-384" | "SHA-512";
  notBefore: number;
  notAfter: number;
  /** Pre-encoded notAfter TLV, for tests that need a syntactically valid but semantically invalid time; overrides `notAfter`. */
  notAfterRaw?: Uint8Array;
  extensions?: Array<[string, Uint8Array]>;
}): Promise<Uint8Array> {
  const sigAlg = seq(oid(SIGNATURE_OIDS[opts.hash]));
  const name = (cn: string) => seq(set(seq(oid("2.5.4.3"), utf8String(cn))));
  const spki = new Uint8Array((await crypto.subtle.exportKey("spki", opts.subjectKey)) as ArrayBuffer);
  const ext = opts.extensions?.length ? [explicit(3, seq(...opts.extensions.map(([id, value]) => seq(oid(id), octet(value)))))] : [];
  const tbs = seq(
    explicit(0, int(2)),
    int(1),
    sigAlg,
    name(opts.issuer),
    seq(utcTime(opts.notBefore), opts.notAfterRaw ?? utcTime(opts.notAfter)),
    name(opts.subject),
    spki,
    ...ext,
  );
  const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: opts.hash }, opts.issuerKey, tbs));
  return seq(tbs, sigAlg, bitString(rawSignatureToDer(raw)));
}

/** Root (P-384) → intermediate (P-384, SHA-384) → leaf (P-256, signed with SHA-256), the shape Apple's App Attest chain has. */
export async function testChain(opts: { nowMs: number; leafExtensions?: Array<[string, Uint8Array]>; leafNotAfter?: number; leafNotAfterRaw?: Uint8Array }) {
  const [rootKey, interKey, leafKey] = await Promise.all([newKey("P-384"), newKey("P-384"), newKey("P-256")]);
  const window = { notBefore: opts.nowMs - DAY, notAfter: opts.nowMs + 365 * DAY };
  const root = await makeCertificate({ subject: "Test Root", issuer: "Test Root", subjectKey: rootKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-384", ...window });
  const intermediate = await makeCertificate({ subject: "Test CA 1", issuer: "Test Root", subjectKey: interKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-384", ...window });
  const leaf = await makeCertificate({
    subject: "leaf",
    issuer: "Test CA 1",
    subjectKey: leafKey.publicKey,
    issuerKey: interKey.privateKey,
    hash: "SHA-256",
    notBefore: opts.nowMs - DAY,
    notAfter: opts.leafNotAfter ?? opts.nowMs + 2 * DAY,
    notAfterRaw: opts.leafNotAfterRaw,
    extensions: opts.leafExtensions,
  });
  return { root, intermediate, leaf, leafKey };
}

export const AAGUID = {
  prod: concat(utf8("appattest"), new Uint8Array(7)),
  dev: utf8("appattestdevelop"),
};

function u32(n: number) {
  const b = new Uint8Array(4);
  new DataView(b.buffer).setUint32(0, n);
  return b;
}

/** A well-formed App Attest attestation from a test CA, with knobs to break each check. */
export async function appAttestFixture(opts: {
  appId: string;
  clientDataHash: Uint8Array;
  nowMs: number;
  counter?: number;
  aaguid?: Uint8Array;
  wrongNonce?: boolean;
  rpAppId?: string;
  /** Reuse a key, so a caller that needs keyId before building the binding can make it first. */
  leafKey?: CryptoKeyPair;
}) {
  const leafKeyPair = opts.leafKey ?? (await newKey("P-256"));
  const point = new Uint8Array((await crypto.subtle.exportKey("raw", leafKeyPair.publicKey)) as ArrayBuffer);
  const keyIdBytes = await sha256(point);
  const cose = encodeCBOR(new Map<number, CBORType>([[1, 2], [3, -7], [-1, 1], [-2, point.subarray(1, 33)], [-3, point.subarray(33)]]));
  const authData = concat(
    await sha256(utf8(opts.rpAppId ?? opts.appId)),
    Uint8Array.of(0x40),
    u32(opts.counter ?? 0),
    opts.aaguid ?? AAGUID.prod,
    Uint8Array.of(0, 32),
    keyIdBytes,
    cose,
  );
  const nonce = await sha256(concat(authData, opts.clientDataHash));
  if (opts.wrongNonce) nonce[0]! ^= 1;
  const chain = await testChainWithLeaf(opts.nowMs, leafKeyPair, [["1.2.840.113635.100.8.2", seq(explicit(1, octet(nonce)))]]);
  const attestation = encodeCBOR(
    new Map<string, CBORType>([
      ["fmt", "apple-appattest"],
      ["attStmt", new Map<string, CBORType>([["x5c", [chain.leaf, chain.intermediate]], ["receipt", new Uint8Array(0)]])],
      ["authData", authData],
    ]),
  );
  const keyId = btoa(String.fromCharCode(...keyIdBytes));
  return { attestation, keyId, root: chain.root, point, leafKey: leafKeyPair };
}

async function testChainWithLeaf(nowMs: number, leafKey: CryptoKeyPair, leafExtensions: Array<[string, Uint8Array]>) {
  const [rootKey, interKey] = await Promise.all([newKey("P-384"), newKey("P-384")]);
  const window = { notBefore: nowMs - DAY, notAfter: nowMs + 365 * DAY };
  const root = await makeCertificate({ subject: "Test Root", issuer: "Test Root", subjectKey: rootKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-384", ...window });
  const intermediate = await makeCertificate({ subject: "Test CA 1", issuer: "Test Root", subjectKey: interKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-384", ...window });
  const leaf = await makeCertificate({ subject: "leaf", issuer: "Test CA 1", subjectKey: leafKey.publicKey, issuerKey: interKey.privateKey, hash: "SHA-256", notBefore: nowMs - DAY, notAfter: nowMs + 2 * DAY, extensions: leafExtensions });
  return { root, intermediate, leaf };
}

/** An App Attest assertion: DER ECDSA-SHA256 over SHA256(authenticatorData ‖ clientDataHash). */
export async function makeAssertion(opts: { privateKey: CryptoKey; appId: string; counter: number; clientDataHash: Uint8Array }) {
  const authenticatorData = concat(await sha256(utf8(opts.appId)), Uint8Array.of(0x00), u32(opts.counter));
  const nonce = await sha256(concat(authenticatorData, opts.clientDataHash));
  const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, opts.privateKey, nonce));
  return encodeCBOR(new Map<string, CBORType>([["signature", rawSignatureToDer(raw)], ["authenticatorData", authenticatorData]]));
}
