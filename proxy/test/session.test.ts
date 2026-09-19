import { describe, expect, it } from "vitest";
import { issueSessionToken, verifySessionToken } from "../src/session";

const KEY = "test-signing-key";
const NOW = 1_800_000_000;

describe("session tokens", () => {
  it("round-trips the device and tier until expiry", async () => {
    const token = await issueSessionToken(KEY, { did: "dev-1", tier: "attested" }, NOW, 3600);
    expect(await verifySessionToken(KEY, token, NOW + 3599)).toEqual({ did: "dev-1", tier: "attested", exp: NOW + 3600 });
  });
  it("rejects an expired token", async () => {
    const token = await issueSessionToken(KEY, { did: "dev-1", tier: "attested" }, NOW, 3600);
    expect(await verifySessionToken(KEY, token, NOW + 3600)).toBeNull();
  });
  it("rejects a token whose tier was edited", async () => {
    const token = await issueSessionToken(KEY, { did: "dev-1", tier: "unattested" }, NOW, 3600);
    const [, sig] = token.split(".");
    const forged = Buffer.from(JSON.stringify({ did: "dev-1", tier: "attested", exp: NOW + 3600 })).toString("base64url") + "." + sig;
    expect(await verifySessionToken(KEY, forged, NOW)).toBeNull();
  });
  it("rejects a token signed with another key and garbage", async () => {
    const token = await issueSessionToken("other-key", { did: "dev-1", tier: "attested" }, NOW, 3600);
    expect(await verifySessionToken(KEY, token, NOW)).toBeNull();
    expect(await verifySessionToken(KEY, "garbage", NOW)).toBeNull();
    expect(await verifySessionToken(KEY, "", NOW)).toBeNull();
  });
});
