import { concat, utf8 } from "../../src/crypto";

const lengthBytes = (n: number) => (n < 0x80 ? [n] : n < 0x100 ? [0x81, n] : [0x82, n >> 8, n & 0xff]);

export const tlv = (tag: number, ...content: Uint8Array[]) => {
  const body = concat(...content);
  return concat(Uint8Array.from([tag, ...lengthBytes(body.length)]), body);
};
export const seq = (...c: Uint8Array[]) => tlv(0x30, ...c);
export const set = (...c: Uint8Array[]) => tlv(0x31, ...c);
export const int = (n: number) => tlv(0x02, Uint8Array.of(n));
export const octet = (b: Uint8Array) => tlv(0x04, b);
export const bitString = (b: Uint8Array) => tlv(0x03, Uint8Array.of(0), b);
export const utf8String = (s: string) => tlv(0x0c, utf8(s));
export const explicit = (n: number, inner: Uint8Array) => tlv(0xa0 + n, inner);

export function oid(dotted: string) {
  const [a, b, ...rest] = dotted.split(".").map(Number);
  const bytes = [a! * 40 + b!];
  for (const n of rest) {
    const chunk = [n & 0x7f];
    for (let v = Math.floor(n / 128); v > 0; v = Math.floor(v / 128)) chunk.unshift((v & 0x7f) | 0x80);
    bytes.push(...chunk);
  }
  return tlv(0x06, Uint8Array.from(bytes));
}

export function utcTime(ms: number) {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return tlv(0x17, utf8(`${p(d.getUTCFullYear() % 100)}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}${p(d.getUTCHours())}${p(d.getUTCMinutes())}${p(d.getUTCSeconds())}Z`));
}

export function rawSignatureToDer(raw: Uint8Array) {
  const half = raw.length / 2;
  const integer = (x: Uint8Array) => {
    let i = 0;
    while (i < x.length - 1 && x[i] === 0) i++;
    const v = x.subarray(i);
    return tlv(0x02, v[0]! & 0x80 ? concat(Uint8Array.of(0), v) : v);
  };
  return seq(integer(raw.subarray(0, half)), integer(raw.subarray(half)));
}
