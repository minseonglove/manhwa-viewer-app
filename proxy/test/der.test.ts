import { describe, expect, it } from "vitest";
import { DerError, OID, parseCertificate, readOid, readTlv, verifyChain } from "../src/attestation/der";
import { DAY, makeCertificate, newKey, testChain } from "./helpers/pki";
import { octet, oid, tlv } from "./helpers/der";
import { utf8 } from "../src/crypto";

const NOW = Date.UTC(2026, 8, 22);

describe("DER reading", () => {
  it("reads OIDs and rejects truncated or indefinite lengths", () => {
    expect(readOid(oid("1.2.840.113635.100.8.2").subarray(2))).toBe("1.2.840.113635.100.8.2");
    expect(() => readTlv(Uint8Array.of(0x30, 0x05, 0x01), 0)).toThrow(DerError);
    expect(() => readTlv(Uint8Array.of(0x30, 0x80, 0x00, 0x00), 0)).toThrow(DerError);
  });
});

describe("parseCertificate", () => {
  it("extracts what chain and App Attest checks need", async () => {
    const { leaf, root } = await testChain({ nowMs: NOW, leafExtensions: [["1.2.840.113635.100.8.2", octet(Uint8Array.of(7))]] });
    const cert = parseCertificate(leaf);
    expect(cert.curve).toBe(OID.p256);
    expect(cert.signatureAlgorithm).toBe(OID.ecdsaSha256);
    expect(cert.publicKey.length).toBe(65);
    expect(cert.publicKey[0]).toBe(4);
    expect([...cert.extensions.get("1.2.840.113635.100.8.2")!]).toEqual([0x04, 0x01, 0x07]);
    expect(cert.notBeforeMs).toBe(NOW - DAY);
    expect(parseCertificate(root).curve).toBe(OID.p384);
  });
  it("throws DerError on anything malformed", async () => {
    const { leaf } = await testChain({ nowMs: NOW });
    expect(() => parseCertificate(leaf.subarray(0, leaf.length - 5))).toThrow(DerError);
    expect(() => parseCertificate(Uint8Array.of(0x30, 0x00))).toThrow(DerError);
  });
  it("throws DerError on an out-of-range UTCTime field (month 13) instead of silently rolling the date", async () => {
    const { leaf } = await testChain({ nowMs: NOW, leafNotAfterRaw: tlv(0x17, utf8("261301000000Z")) });
    expect(() => parseCertificate(leaf)).toThrow(DerError);
  });
  it("throws DerError on an impossible UTCTime date (February 30) instead of silently rolling into March", async () => {
    const { leaf } = await testChain({ nowMs: NOW, leafNotAfterRaw: tlv(0x17, utf8("260230000000Z")) });
    expect(() => parseCertificate(leaf)).toThrow(DerError);
  });
});

describe("verifyChain", () => {
  it("accepts leaf ← intermediate ← pinned root, including P-384 signing with SHA-256", async () => {
    const { leaf, intermediate, root } = await testChain({ nowMs: NOW });
    expect(await verifyChain([parseCertificate(leaf), parseCertificate(intermediate)], [parseCertificate(root)], NOW)).toBe(true);
  });
  it("rejects an unknown root, a broken link, a tampered signature and expiry", async () => {
    const a = await testChain({ nowMs: NOW });
    const b = await testChain({ nowMs: NOW });
    const chain = [parseCertificate(a.leaf), parseCertificate(a.intermediate)];
    expect(await verifyChain(chain, [parseCertificate(b.root)], NOW)).toBe(false);
    expect(await verifyChain([parseCertificate(a.leaf), parseCertificate(b.intermediate)], [parseCertificate(b.root)], NOW)).toBe(false);
    const tampered = new Uint8Array(a.leaf);
    tampered[tampered.length - 3]! ^= 0x01;
    expect(await verifyChain([parseCertificate(tampered), chain[1]!], [parseCertificate(a.root)], NOW)).toBe(false);
    expect(await verifyChain(chain, [parseCertificate(a.root)], NOW + 3 * DAY)).toBe(false);
    expect(await verifyChain([], [parseCertificate(a.root)], NOW)).toBe(false);
  });
  it("treats an unsupported curve as unverifiable rather than throwing", async () => {
    const rootKey = await newKey("P-521");
    const leafKey = await newKey("P-256");
    const window = { notBefore: NOW - DAY, notAfter: NOW + DAY };
    const root = await makeCertificate({ subject: "Root521", issuer: "Root521", subjectKey: rootKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-256", ...window });
    const leaf = await makeCertificate({ subject: "leaf", issuer: "Root521", subjectKey: leafKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-256", ...window });
    await expect(verifyChain([parseCertificate(leaf)], [parseCertificate(root)], NOW)).resolves.toBe(false);
  });
  it("treats an unsupported signature hash as unverifiable rather than throwing", async () => {
    const rootKey = await newKey("P-384");
    const leafKey = await newKey("P-256");
    const window = { notBefore: NOW - DAY, notAfter: NOW + DAY };
    const root = await makeCertificate({ subject: "Root384", issuer: "Root384", subjectKey: rootKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-384", ...window });
    const leaf = await makeCertificate({ subject: "leaf-sha512", issuer: "Root384", subjectKey: leafKey.publicKey, issuerKey: rootKey.privateKey, hash: "SHA-512", ...window });
    await expect(verifyChain([parseCertificate(leaf)], [parseCertificate(root)], NOW)).resolves.toBe(false);
  });
});
