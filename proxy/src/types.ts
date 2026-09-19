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

export interface Env {
  AI: AiBinding;
  SESSION_SIGNING_KEY: string;
  DEEPINFRA_API_KEY: string;
  GOOGLE_TRANSLATE_API_KEY: string;
  DEV_ATTESTATION_SECRET?: string;
}
