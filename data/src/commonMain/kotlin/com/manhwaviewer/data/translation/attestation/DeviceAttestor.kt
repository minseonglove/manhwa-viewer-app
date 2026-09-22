package com.manhwaviewer.data.translation.attestation

/** Fields a platform adds to the proxy's session request. The app stores credentials but never reads them. */
data class AttestationProof(
    val platform: String,
    val publicKey: String? = null,
    val integrityToken: String? = null,
    val devSecret: String? = null,
    val keyId: String? = null,
    val attestation: String? = null,
    val assertion: String? = null,
    val credential: String? = null,
    val signature: String? = null,
)

sealed interface AttestationResult {
    data class Proof(val proof: AttestationProof) : AttestationResult

    /** This device cannot attest at all (no Play services, App Attest unsupported); retrying will not help. */
    data object Unavailable : AttestationResult

    /** Attestation failed for now; the proxy client opens a fresh session in a few minutes. */
    data object TransientFailure : AttestationResult
}

/**
 * Proves to the translation proxy that requests come from a genuine install of this app.
 * Implementations never throw for attestation problems; they answer [AttestationResult.Unavailable] or [AttestationResult.TransientFailure].
 */
interface DeviceAttestor {
    suspend fun prove(challenge: String, deviceId: String): AttestationResult
    suspend fun onCredential(credential: String, recheckAfterSeconds: Long?)
    suspend fun onCredentialRejected()
}
