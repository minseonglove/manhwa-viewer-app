import { describe, expect, it } from "vitest";
import { GoogleAccessTokens } from "../src/attestation/google-oauth";
import { MOCK_GOOGLE_ACCESS_TOKEN } from "./google-oauth-mock";

describe("GoogleAccessTokens in workerd", () => {
  // workerd's global fetch throws "Illegal invocation" when called as a method of another object
  // (e.g. `this.fetchFn(...)`); Node's fetch and the Node test mocks do not care about `this`, so only
  // a real workerd fetch, injected as-is, can catch a regression here.
  it("fetches an access token through the injected global fetch, unbound", async () => {
    const pair = (await crypto.subtle.generateKey(
      { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
      true,
      ["sign"],
    )) as CryptoKeyPair;
    const pkcs8 = new Uint8Array((await crypto.subtle.exportKey("pkcs8", pair.privateKey)) as ArrayBuffer);
    const sa = {
      client_email: "svc@x.iam.gserviceaccount.com",
      private_key: `-----BEGIN PRIVATE KEY-----\n${btoa(String.fromCharCode(...pkcs8))}\n-----END PRIVATE KEY-----\n`,
    };
    const tokens = new GoogleAccessTokens(fetch);
    await expect(tokens.get(sa, Math.floor(Date.now() / 1000))).resolves.toBe(MOCK_GOOGLE_ACCESS_TOKEN);
  });
});
