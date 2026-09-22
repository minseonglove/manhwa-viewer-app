import { describe, expect, it } from "vitest";
import { CHALLENGE_TTL_SECONDS, computeBinding, issueChallenge, verifyChallenge } from "../src/challenge";
import { b64url } from "../src/crypto";
import { issueSessionToken } from "../src/session";

const KEY = "signing-key";
const NOW = 1_800_000_000;
const hex = (b: Uint8Array) => [...b].map((x) => x.toString(16).padStart(2, "0")).join("");

describe("challenges", () => {
  it("verify until they expire", async () => {
    const c = await issueChallenge(KEY, NOW);
    expect(await verifyChallenge(KEY, c, NOW + CHALLENGE_TTL_SECONDS - 1)).toBe(true);
    expect(await verifyChallenge(KEY, c, NOW + CHALLENGE_TTL_SECONDS)).toBe(false);
  });
  it("are unique", async () => {
    expect(await issueChallenge(KEY, NOW)).not.toBe(await issueChallenge(KEY, NOW));
  });
  it("reject other keys, edits, session tokens and oversized input", async () => {
    const c = await issueChallenge(KEY, NOW);
    expect(await verifyChallenge("other", c, NOW)).toBe(false);
    expect(await verifyChallenge(KEY, c.slice(0, -2), NOW)).toBe(false);
    expect(await verifyChallenge(KEY, await issueSessionToken(KEY, { did: "d", tier: "attested" }, NOW, 60), NOW)).toBe(false);
    expect(await verifyChallenge(KEY, "x".repeat(300), NOW)).toBe(false);
  });
});

describe("computeBinding", () => {
  // The same vectors are asserted by the app's SessionBindingTest; both sides must agree byte for byte.
  it.each([
    ["c", "d", "k", "8eab13fe9169245e0c3e752e78e10d461f41f94b2383e530f6895d0d304ddcca", "jqsT_pFpJF4MPnUueOENRh9B-Usjg-Uw9oldDTBN3Mo"],
    ["chal.sig", "0123456789abcdef0123456789abcdef", "zgSY9YSD+7TaDXssY6WlOPVS1K3Lmk+pFhlcSWE+ZV0=", "6b2e56dd3461328aeaaaf832401ce623386c2158c6efc13690a2a2dc01f277c1", "ay5W3TRhMorqqvgyQBzmIzhsIVjG78E2kKKi3AHyd8E"],
    ["한", "d", "", "b989cd042b90d56b682cd02c2cca9705a93ce75bdec0423e4a5922282a3bc551", "uYnNBCuQ1WtoLNAsLMqXBak851vewEI-SlkiKCo7xVE"],
  ])("binds %j %j %j", async (challenge, deviceId, key, expectedHex, expectedB64) => {
    const binding = await computeBinding(challenge, deviceId, key);
    expect(hex(binding)).toBe(expectedHex);
    expect(b64url(binding)).toBe(expectedB64);
  });
});
