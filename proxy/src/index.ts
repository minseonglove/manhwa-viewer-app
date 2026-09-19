import { deviceHash, verifyAttestation } from "./attestation";
import { ChainError, runChain } from "./chain";
import { deepseekEngine, gemmaEngine, googleEngine } from "./engines";
import { filterGlossary } from "./glossary";
import { validateTranslateRequest } from "./limits";
import { issueSessionToken, verifySessionToken } from "./session";
import type { Env, Tier } from "./types";

const SESSION_TTL_SECONDS = 12 * 60 * 60;
const MAX_DEVICE_ID_LENGTH = 128;

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const nowSeconds = () => Math.floor(Date.now() / 1000);

async function readJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return undefined;
  }
}

export function createHandler(fetchFn: typeof fetch) {
  async function createSession(request: Request, env: Env): Promise<Response> {
    const body = await readJson(request);
    const deviceId = (body as { deviceId?: unknown } | undefined)?.deviceId;
    if (!body || typeof body !== "object" || typeof deviceId !== "string" || !deviceId || deviceId.length > MAX_DEVICE_ID_LENGTH) {
      return json(400, { error: "deviceId is required" });
    }
    const tier: Tier = (await verifyAttestation(body as Record<string, unknown>, env)) ? "attested" : "unattested";
    const token = await issueSessionToken(env.SESSION_SIGNING_KEY, { did: await deviceHash(deviceId), tier }, nowSeconds(), SESSION_TTL_SECONDS);
    return json(200, { token, tier, expiresIn: SESSION_TTL_SECONDS });
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
      const result = await runChain(engines, items, glossary);
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
    const route = pathname === "/v1/session" ? createSession : pathname === "/v1/translate" ? translate : undefined;
    if (!route) return json(404, { error: "not found" });
    if (request.method !== "POST") return json(405, { error: "POST only" });
    return route(request, env);
  };
}

export default { fetch: createHandler(fetch) } satisfies ExportedHandler<Env>;
