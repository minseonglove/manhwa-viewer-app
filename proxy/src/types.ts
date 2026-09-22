import type { AppAttestKey } from "./attestation/key-store";

export const KINDS = ["bubble", "narration", "sfx", "background", "aside"] as const;
export type Kind = (typeof KINDS)[number];

export interface Item {
  id: string;
  kind: Kind;
  text: string;
}

export interface GlossaryEntry {
  src: string;
  ko: string;
}

export interface TranslateRequest {
  items: Item[];
  source: string;
  target: string;
  glossary: GlossaryEntry[];
}

export type Tier = "attested" | "unattested";

/** The part of the Workers AI binding this Worker uses. */
export interface AiBinding {
  run(model: string, input: unknown): Promise<unknown>;
}

export interface RateLimiter {
  limit(options: { key: string }): Promise<{ success: boolean }>;
}

export interface Env {
  AI: AiBinding;
  SESSION_SIGNING_KEY: string;
  DEEPINFRA_API_KEY: string;
  GOOGLE_TRANSLATE_API_KEY: string;
  DEV_ATTESTATION_SECRET?: string;
  PLAY_INTEGRITY_SERVICE_ACCOUNT?: string;
  ANDROID_PACKAGE_NAME: string;
  IOS_APP_ID: string;
  APP_ATTEST_KEYS?: DurableObjectNamespace<AppAttestKey>;
  REGISTRATION_LIMITER?: RateLimiter;
}
