import { describe, expect, it } from "vitest";
import { issueCredential, verifyCredential } from "../src/attestation/credential";
import { issueChallenge } from "../src/challenge";

const KEY = "signing-key";
const android = { p: "a" as const, k: "kid", pk: "spki", d: "did", va: 100 };
const ios = { p: "i" as const, k: "keyId", pk: "point", d: "did", env: "prod" as const, va: 100 };

describe("credentials", () => {
  it("round-trip for both platforms", async () => {
    expect(await verifyCredential(KEY, await issueCredential(KEY, android))).toEqual({ v: 1, ...android });
    expect(await verifyCredential(KEY, await issueCredential(KEY, ios))).toEqual({ v: 1, ...ios });
  });
  it("reject other keys, edits, challenges and oversized input", async () => {
    const token = await issueCredential(KEY, android);
    expect(await verifyCredential("other", token)).toBeNull();
    expect(await verifyCredential(KEY, token.slice(1))).toBeNull();
    expect(await verifyCredential(KEY, await issueChallenge(KEY, 0))).toBeNull();
    expect(await verifyCredential(KEY, "x".repeat(3000))).toBeNull();
  });
  it("require an environment on iOS credentials", async () => {
    const { env: _omit, ...noEnv } = ios;
    expect(await verifyCredential(KEY, await issueCredential(KEY, noEnv))).toBeNull();
  });
});
