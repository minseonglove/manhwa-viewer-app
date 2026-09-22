import { attest, deviceHash } from "./attestation";
import { GoogleAccessTokens } from "./attestation/google-oauth";
import { durableKeyStore, type KeyStore } from "./attestation/key-store-client";
import { ChainError, runChain } from "./chain";
import { CHALLENGE_TTL_SECONDS, issueChallenge } from "./challenge";
import { CHAIN_DEADLINE_MS, deepseekEngine, gemmaEngine, googleEngine } from "./engines";
import { filterGlossary } from "./glossary";
import { validateTranslateRequest } from "./limits";
import { issueSessionToken, verifySessionToken } from "./session";
import type { Env } from "./types";

const SESSION_TTL_SECONDS = 12 * 60 * 60;
export const TRANSIENT_SESSION_TTL_SECONDS = 10 * 60;
const MAX_DEVICE_ID_LENGTH = 128;
const PLATFORMS = new Set(["dev", "android", "ios"]);

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

async function readJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return undefined;
  }
}

export interface HandlerOptions {
  keyStore?: KeyStore;
  nowMs?: () => number;
}

export function createHandler(fetchFn: typeof fetch, options: HandlerOptions = {}) {
  // Both live as long as the isolate: the access token is reused across requests, and a quota backoff applies to all of them.
  const googleTokens = new GoogleAccessTokens(fetchFn);
  const backoff = { untilMs: 0 };
  const now = options.nowMs ?? (() => Date.now());
  const nowSeconds = () => Math.floor(now() / 1000);

  async function challenge(_request: Request, env: Env): Promise<Response> {
    return json(200, { challenge: await issueChallenge(env.SESSION_SIGNING_KEY, nowSeconds()), expiresIn: CHALLENGE_TTL_SECONDS });
  }

  async function createSession(request: Request, env: Env): Promise<Response> {
    const body = await readJson(request);
    const deviceId = (body as { deviceId?: unknown } | undefined)?.deviceId;
    if (!body || typeof body !== "object" || typeof deviceId !== "string" || !deviceId || deviceId.length > MAX_DEVICE_ID_LENGTH) {
      return json(400, { error: "deviceId is required" });
    }
    const fields = body as Record<string, unknown>;
    const did = await deviceHash(deviceId);
    const outcome = await attest(fields, {
      deviceId,
      did,
      env,
      fetchFn,
      keyStore: options.keyStore ?? (env.APP_ATTEST_KEYS ? durableKeyStore(env.APP_ATTEST_KEYS) : null),
      googleTokens,
      backoff,
      nowMs: now(),
      clientIp: request.headers.get("cf-connecting-ip"),
    });
    // A device told its credential is invalid can only re-register when it opens its next session, so that session comes soon.
    const short = outcome.tier === "unattested" && (outcome.transient || outcome.credentialStatus === "invalid");
    const ttl = short ? TRANSIENT_SESSION_TTL_SECONDS : SESSION_TTL_SECONDS;
    const token = await issueSessionToken(env.SESSION_SIGNING_KEY, { did, tier: outcome.tier }, nowSeconds(), ttl);
    const platform = typeof fields.platform === "string" && PLATFORMS.has(fields.platform) ? fields.platform : null;
    console.log(JSON.stringify({ event: "session", did, platform, tier: outcome.tier, reason: outcome.reason }));
    return json(200, {
      token,
      tier: outcome.tier,
      expiresIn: ttl,
      ...(outcome.credential ? { credential: outcome.credential } : {}),
      ...(outcome.credentialStatus ? { credentialStatus: outcome.credentialStatus } : {}),
      ...(outcome.recheckAfter !== undefined ? { recheckAfter: outcome.recheckAfter } : {}),
    });
  }

  async function translate(request: Request, env: Env): Promise<Response> {
    const bearer = request.headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
    const session = bearer ? await verifySessionToken(env.SESSION_SIGNING_KEY, bearer, nowSeconds()) : null;
    if (!session) return json(401, { error: "a valid session token is required" });

    const validation = validateTranslateRequest(await readJson(request));
    if (!validation.ok) return json(400, { error: validation.error });
    const { items } = validation.request;
    const glossary = filterGlossary(validation.request.glossary, items);

    const google = googleEngine({ apiKey: env.GOOGLE_TRANSLATE_API_KEY }, fetchFn);
    // Paid engines are only for installs that proved themselves; everyone else still gets a translation from the free tier.
    const engines =
      session.tier === "attested"
        ? [gemmaEngine(env.AI), deepseekEngine({ apiKey: env.DEEPINFRA_API_KEY }, fetchFn), google]
        : [google];

    const meter = { did: session.did, tier: session.tier, items: items.length, chars: items.reduce((n, it) => n + [...it.text].length, 0), glossary: glossary.length };
    try {
      const result = await runChain(engines, items, glossary, CHAIN_DEADLINE_MS);
      console.log(JSON.stringify({ ...meter, engine: result.engine, attempts: result.attempts }));
      return json(200, { translations: result.translations, engine: result.engine });
    } catch (e) {
      if (!(e instanceof ChainError)) throw e;
      console.log(JSON.stringify({ ...meter, engine: null, attempts: e.attempts }));
      return json(502, { error: "all translation engines failed", attempts: e.attempts });
    }
  }

  return async function handle(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    const route = pathname === "/v1/session" ? createSession : pathname === "/v1/translate" ? translate : pathname === "/v1/challenge" ? challenge : undefined;
    if (!route) return json(404, { error: "not found" });
    if (request.method !== "POST") return json(405, { error: "POST only" });
    return route(request, env);
  };
}

export default { fetch: createHandler(fetch) } satisfies ExportedHandler<Env>;
