import { DurableObject } from "cloudflare:workers";
import type { AppAttestEnvironment } from "./app-attest";
import type { AdvanceResult, RegisterResult } from "./key-store-client";

/** One object per App Attest key id. Each method runs without awaiting, so its read and write cannot interleave with another request. */
export class AppAttestKey extends DurableObject<unknown> {
  constructor(ctx: DurableObjectState, env: unknown) {
    super(ctx, env);
    ctx.storage.sql.exec(
      "CREATE TABLE IF NOT EXISTS app_attest_key (id INTEGER PRIMARY KEY CHECK (id = 1), env TEXT NOT NULL, device_hash TEXT NOT NULL, counter INTEGER NOT NULL, created_at INTEGER NOT NULL)",
    );
  }

  register(env: AppAttestEnvironment, deviceHash: string, nowSec: number): RegisterResult {
    const cursor = this.ctx.storage.sql.exec(
      "INSERT INTO app_attest_key (id, env, device_hash, counter, created_at) VALUES (1, ?, ?, 0, ?) ON CONFLICT(id) DO NOTHING",
      env,
      deviceHash,
      nowSec,
    );
    return cursor.rowsWritten === 1 ? "registered" : "exists";
  }

  advance(counter: number, deviceHash: string): AdvanceResult {
    const row = this.ctx.storage.sql.exec<{ device_hash: string; counter: number }>("SELECT device_hash, counter FROM app_attest_key WHERE id = 1").toArray()[0];
    if (!row) return "unknown";
    if (row.device_hash !== deviceHash) return "device_mismatch";
    if (counter <= row.counter) return "replay";
    this.ctx.storage.sql.exec("UPDATE app_attest_key SET counter = ? WHERE id = 1", counter);
    return "ok";
  }
}
