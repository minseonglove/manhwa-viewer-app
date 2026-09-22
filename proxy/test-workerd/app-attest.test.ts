import { describe, expect, it } from "vitest";
import { APPLE_APP_ATTESTATION_ROOT } from "../src/attestation/apple-root";
import { verifyAppAttestation } from "../src/attestation/app-attest";
import { fromBase64, utf8 } from "../src/crypto";
import { APPLE_SAMPLE } from "../test/fixtures/apple-sample-attestation";

describe("App Attest in workerd", () => {
  // Apple's chain needs P-384 with SHA-384 (root → CA) and P-384 with SHA-256 (CA → leaf); this proves workerd's WebCrypto takes both.
  it("verifies Apple's sample chain", async () => {
    const verdict = await verifyAppAttestation({
      attestation: fromBase64(APPLE_SAMPLE.attestation),
      keyId: APPLE_SAMPLE.keyId,
      clientDataHash: utf8(APPLE_SAMPLE.clientData),
      appId: APPLE_SAMPLE.appId,
      roots: [APPLE_APP_ATTESTATION_ROOT],
      nowMs: APPLE_SAMPLE.validAtMs,
    });
    expect(verdict.ok).toBe(true);
  });
});
