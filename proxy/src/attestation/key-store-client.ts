import type { AppAttestEnvironment } from "./app-attest";
import type { AppAttestKey } from "./key-store";

export type RegisterResult = "registered" | "exists";
export type AdvanceResult = "ok" | "unknown" | "replay" | "device_mismatch";

/** Per App Attest key: registered once, its counter only moves forward, and it answers for one install. */
export interface KeyStore {
  register(keyId: string, env: AppAttestEnvironment, deviceHash: string, nowSec: number): Promise<RegisterResult>;
  advance(keyId: string, counter: number, deviceHash: string): Promise<AdvanceResult>;
}

export function durableKeyStore(ns: DurableObjectNamespace<AppAttestKey>): KeyStore {
  const stub = (keyId: string) => ns.get(ns.idFromName(keyId));
  return {
    register: (keyId, env, deviceHash, nowSec) => stub(keyId).register(env, deviceHash, nowSec),
    advance: (keyId, counter, deviceHash) => stub(keyId).advance(counter, deviceHash),
  };
}
