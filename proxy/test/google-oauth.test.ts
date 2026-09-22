import { describe, expect, it } from "vitest";
import { b64url, fromBase64, utf8 } from "../src/crypto";
import { GOOGLE_TOKEN_URL, GoogleAccessTokens, PLAY_INTEGRITY_SCOPE, parseServiceAccount } from "../src/attestation/google-oauth";

async function testAccount() {
  const pair = (await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  )) as CryptoKeyPair;
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey) as ArrayBuffer);
  const b64 = btoa(String.fromCharCode(...pkcs8)).replace(/(.{64})/g, "$1\n");
  return { sa: { client_email: "svc@proj.iam.gserviceaccount.com", private_key: `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n` }, publicKey: pair.publicKey };
}

function tokenEndpoint(reply: () => Response) {
  const bodies: string[] = [];
  const fn = (async (url: string, init?: RequestInit) => {
    expect(url).toBe(GOOGLE_TOKEN_URL);
    bodies.push(String(init?.body));
    return reply();
  }) as unknown as typeof fetch;
  return { fn, bodies };
}

describe("service account parsing", () => {
  it("accepts the JSON key file shape and rejects anything else", () => {
    expect(parseServiceAccount('{"client_email":"a","private_key":"b","type":"service_account"}')).toEqual({ client_email: "a", private_key: "b" });
    expect(parseServiceAccount(undefined)).toBeNull();
    expect(parseServiceAccount("{")).toBeNull();
    expect(parseServiceAccount('{"client_email":"a"}')).toBeNull();
  });
});

describe("GoogleAccessTokens", () => {
  it("exchanges a correctly signed RS256 assertion", async () => {
    const { sa, publicKey } = await testAccount();
    const endpoint = tokenEndpoint(() => new Response(JSON.stringify({ access_token: "at-1", expires_in: 3599, token_type: "Bearer" })));
    expect(await new GoogleAccessTokens(endpoint.fn).get(sa, 1000)).toBe("at-1");

    const form = new URLSearchParams(endpoint.bodies[0]);
    expect(form.get("grant_type")).toBe("urn:ietf:params:oauth:grant-type:jwt-bearer");
    const [header, claims, signature] = form.get("assertion")!.split(".");
    expect(JSON.parse(new TextDecoder().decode(fromBase64(header!)))).toEqual({ alg: "RS256", typ: "JWT" });
    expect(JSON.parse(new TextDecoder().decode(fromBase64(claims!)))).toEqual({ iss: sa.client_email, scope: PLAY_INTEGRITY_SCOPE, aud: GOOGLE_TOKEN_URL, iat: 1000, exp: 4600 });
    expect(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, fromBase64(signature!), utf8(`${header}.${claims}`))).toBe(true);
    expect(b64url(fromBase64(signature!))).toBe(signature);
  });
  it("reuses a token until five minutes before it expires", async () => {
    const { sa } = await testAccount();
    let n = 0;
    const endpoint = tokenEndpoint(() => new Response(JSON.stringify({ access_token: `at-${++n}`, expires_in: 3600 })));
    const tokens = new GoogleAccessTokens(endpoint.fn);
    expect(await tokens.get(sa, 0)).toBe("at-1");
    expect(await tokens.get(sa, 3299)).toBe("at-1");
    expect(await tokens.get(sa, 3300)).toBe("at-2");
    expect(endpoint.bodies).toHaveLength(2);
  });
  it("fails without echoing the response body", async () => {
    const { sa } = await testAccount();
    const tokens = new GoogleAccessTokens(tokenEndpoint(() => new Response("secret detail", { status: 401 })).fn);
    await expect(tokens.get(sa, 0)).rejects.toThrow(/^oauth HTTP 401$/);
    const malformed = new GoogleAccessTokens(tokenEndpoint(() => new Response("{}")).fn);
    await expect(malformed.get(sa, 0)).rejects.toThrow("oauth response malformed");
  });
  it("gives up on a token endpoint that does not answer, with a fixed message", async () => {
    const { sa } = await testAccount();
    const signals: (AbortSignal | null | undefined)[] = [];
    const hang = (async (_url: string, init?: RequestInit) => {
      signals.push(init?.signal);
      return new Promise<Response>((_, reject) => init?.signal?.addEventListener("abort", () => reject(new Error("secret detail"))));
    }) as unknown as typeof fetch;
    await expect(new GoogleAccessTokens(hang, 20).get(sa, 0)).rejects.toThrow(/^oauth timeout$/);
    expect(signals[0]).toBeInstanceOf(AbortSignal);
    const unreachable = (async () => Promise.reject(new Error("secret detail"))) as unknown as typeof fetch;
    await expect(new GoogleAccessTokens(unreachable).get(sa, 0)).rejects.toThrow(/^oauth request failed$/);
  });
  it("drops the cached token only when that token is the one rejected", async () => {
    const { sa } = await testAccount();
    let n = 0;
    const endpoint = tokenEndpoint(() => new Response(JSON.stringify({ access_token: `at-${++n}`, expires_in: 3600 })));
    const tokens = new GoogleAccessTokens(endpoint.fn);
    expect(await tokens.get(sa, 0)).toBe("at-1");
    tokens.invalidate("at-1");
    expect(await tokens.get(sa, 1)).toBe("at-2");
    tokens.invalidate("at-1");
    expect(await tokens.get(sa, 2)).toBe("at-2");
    expect(endpoint.bodies).toHaveLength(2);
  });
  it("fails with fixed message on malformed key", async () => {
    const badSa = { client_email: "svc@proj.iam.gserviceaccount.com", private_key: "-----BEGIN PRIVATE KEY-----\nnot a key\n-----END PRIVATE KEY-----\n" };
    const endpoint = tokenEndpoint(() => new Response(JSON.stringify({ access_token: "at", expires_in: 3600 })));
    await expect(new GoogleAccessTokens(endpoint.fn).get(badSa, 0)).rejects.toThrow("oauth key invalid");
    expect(endpoint.bodies).toHaveLength(0);
  });
});
