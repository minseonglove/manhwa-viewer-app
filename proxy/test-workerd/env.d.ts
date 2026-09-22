import type { RateLimiter, Env as WorkerEnv } from "../src/types";
import type { AppAttestKey } from "../src/attestation/key-store";

declare global {
  namespace Cloudflare {
    // The test Worker's wrangler.toml always binds these, unlike the production Env where they are optional.
    interface Env extends WorkerEnv {
      APP_ATTEST_KEYS: DurableObjectNamespace<AppAttestKey>;
      REGISTRATION_LIMITER: RateLimiter;
    }
  }
}
