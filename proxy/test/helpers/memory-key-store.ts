import type { KeyStore } from "../../src/attestation/key-store-client";

/** The Durable Object's rules in memory, for Node tests. test-workerd/key-store.test.ts pins the real object to the same rules. */
export function memoryKeyStore(): KeyStore {
  const keys = new Map<string, { deviceHash: string; counter: number }>();
  return {
    async register(keyId, _env, deviceHash) {
      if (keys.has(keyId)) return "exists";
      keys.set(keyId, { deviceHash, counter: 0 });
      return "registered";
    },
    async advance(keyId, counter, deviceHash) {
      const key = keys.get(keyId);
      if (!key) return "unknown";
      if (key.deviceHash !== deviceHash) return "device_mismatch";
      if (counter <= key.counter) return "replay";
      key.counter = counter;
      return "ok";
    },
  };
}
