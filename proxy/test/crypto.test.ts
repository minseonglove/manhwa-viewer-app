import { describe, expect, it } from "vitest";
import { b64url, derivedHmacKey, derSignatureToRaw, equalBytes, fromBase64, hmacKey, openToken, sha256, signToken, utf8 } from "../src/crypto";

const hex = (b: Uint8Array) => [...b].map((x) => x.toString(16).padStart(2, "0")).join("");

describe("encoding", () => {
  it("round-trips base64url without padding and reads standard base64 too", () => {
    const bytes = Uint8Array.from([0xfb, 0xff, 0x00, 0x10]);
    expect(b64url(bytes)).toBe("-_8AEA");
    expect([...fromBase64("-_8AEA")]).toEqual([...bytes]);
    expect([...fromBase64("+/8AEA==")]).toEqual([...bytes]);
    expect(() => fromBase64("not base64!")).toThrow();
  });
  it("hashes with SHA-256", async () => {
    expect(hex(await sha256(utf8("abc")))).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  });
  it("compares bytes", () => {
    expect(equalBytes(Uint8Array.of(1, 2), Uint8Array.of(1, 2))).toBe(true);
    expect(equalBytes(Uint8Array.of(1, 2), Uint8Array.of(1, 3))).toBe(false);
    expect(equalBytes(Uint8Array.of(1), Uint8Array.of(1, 2))).toBe(false);
  });
});

describe("signed tokens", () => {
  it("opens what it signed and nothing else", async () => {
    const key = await hmacKey("secret");
    const token = await signToken(key, { a: 1 });
    expect(await openToken(key, token)).toEqual({ a: 1 });
    expect(await openToken(await hmacKey("other"), token)).toBeNull();
    expect(await openToken(key, token.replace(/.$/, (c) => (c === "A" ? "B" : "A")))).toBeNull();
    expect(await openToken(key, "garbage")).toBeNull();
    expect(await openToken(key, "a.b.c")).toBeNull();
  });
  it("keeps tokens for one purpose from opening as another", async () => {
    const token = await signToken(await derivedHmacKey("secret", "challenge-v1"), { t: "c" });
    expect(await openToken(await derivedHmacKey("secret", "credential-v1"), token)).toBeNull();
    expect(await openToken(await hmacKey("secret"), token)).toBeNull();
  });
});

describe("derSignatureToRaw", () => {
  it("matches the raw signature WebCrypto produces", async () => {
    const pair = (await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"])) as CryptoKeyPair;
    const data = utf8("payload");
    for (let i = 0; i < 20; i++) {
      const raw = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, pair.privateKey, data));
      const der = rawToDer(raw);
      expect(hex(derSignatureToRaw(der, 32))).toBe(hex(raw));
    }
  });
  it("rejects malformed input", () => {
    expect(() => derSignatureToRaw(Uint8Array.of(0x31, 0x00), 32)).toThrow();
    expect(() => derSignatureToRaw(Uint8Array.of(0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01), 32)).toThrow();
    const tooLong = Uint8Array.of(0x30, 0x26, 0x02, 0x21, ...new Uint8Array(33).fill(1), 0x02, 0x01, 0x01);
    expect(() => derSignatureToRaw(tooLong, 32)).toThrow();
  });
});

function rawToDer(raw: Uint8Array): Uint8Array {
  const half = raw.length / 2;
  const integer = (x: Uint8Array) => {
    let i = 0;
    while (i < x.length - 1 && x[i] === 0) i++;
    const v = x[i]! & 0x80 ? [0, ...x.subarray(i)] : [...x.subarray(i)];
    return [0x02, v.length, ...v];
  };
  const body = [...integer(raw.subarray(0, half)), ...integer(raw.subarray(half))];
  return Uint8Array.from([0x30, body.length, ...body]);
}
