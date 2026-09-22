package com.manhwaviewer.data.translation.attestation

import com.manhwaviewer.data.preference.AttestationStateStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.DeviceCheck.DCAppAttestService
import platform.DeviceCheck.DCError
import platform.DeviceCheck.DCErrorDomain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.resume

/**
 * App Attest: one key per install, attested once, then an assertion per session. Apple limits how often keys may be
 * attested and never attests a key twice, so a key is replaced only when the proxy no longer accepts it.
 */
@OptIn(ExperimentalForeignApi::class)
class AppAttestAttestor(private val store: AttestationStateStore) : DeviceAttestor {

    private val service get() = DCAppAttestService.sharedService

    private sealed interface Outcome {
        data class Ok(val bytes: ByteArray) : Outcome
        data object InvalidKey : Outcome
        data object Failed : Outcome
    }

    companion object {
        // DeviceCheck's completion handlers can hang indefinitely with no server response; past this the session opens unattested.
        private const val PROVE_TIMEOUT_MS = 10_000L
    }

    override suspend fun prove(challenge: String, deviceId: String): AttestationResult =
        withTimeoutOrNull(PROVE_TIMEOUT_MS) {
            try {
                proveNow(challenge, deviceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AttestationResult.TransientFailure
            }
        } ?: AttestationResult.TransientFailure

    private suspend fun proveNow(challenge: String, deviceId: String): AttestationResult {
        if (!service.supported) return AttestationResult.Unavailable
        val state = store.read()
        val keyId = state.keyId ?: (generateKey() ?: return AttestationResult.TransientFailure).also { store.saveKeyId(it) }
        val clientDataHash = SessionBinding.compute(challenge, deviceId, keyId).toNSData()

        val credential = state.credential
        return if (credential == null) {
            when (val outcome = attest(keyId, clientDataHash)) {
                is Outcome.Ok -> AttestationResult.Proof(AttestationProof(platform = "ios", keyId = keyId, attestation = SessionBinding.base64Url(outcome.bytes)))
                // Already attested but the credential never arrived: this key can no longer register, so start over with a new one.
                Outcome.InvalidKey -> AttestationResult.TransientFailure.also { store.clearAll() }
                Outcome.Failed -> AttestationResult.TransientFailure
            }
        } else {
            when (val outcome = assert(keyId, clientDataHash)) {
                is Outcome.Ok -> AttestationResult.Proof(AttestationProof(platform = "ios", credential = credential, assertion = SessionBinding.base64Url(outcome.bytes)))
                Outcome.InvalidKey -> AttestationResult.TransientFailure.also { store.clearAll() }
                Outcome.Failed -> AttestationResult.TransientFailure
            }
        }
    }

    override suspend fun onCredential(credential: String, recheckAfterSeconds: Long?) {
        try {
            store.saveCredential(credential, 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort: the next prove() re-attests without a stored credential.
        }
    }

    override suspend fun onCredentialRejected() {
        try {
            store.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort: the next prove() re-attests without a stored credential.
        }
    }

    private suspend fun generateKey(): String? = suspendCancellableCoroutine { continuation ->
        service.generateKeyWithCompletionHandler { keyId, _ ->
            if (continuation.isActive) continuation.resume(keyId)
        }
    }

    private suspend fun attest(keyId: String, clientDataHash: NSData): Outcome = suspendCancellableCoroutine { continuation ->
        service.attestKey(keyId, clientDataHash) { data, error ->
            if (continuation.isActive) continuation.resume(outcome(data, error))
        }
    }

    private suspend fun assert(keyId: String, clientDataHash: NSData): Outcome = suspendCancellableCoroutine { continuation ->
        service.generateAssertion(keyId, clientDataHash) { data, error ->
            if (continuation.isActive) continuation.resume(outcome(data, error))
        }
    }

    private fun outcome(data: NSData?, error: NSError?): Outcome = when {
        data != null -> Outcome.Ok(data.toByteArray())
        error != null && error.domain == DCErrorDomain && error.code == DCError.DCErrorInvalidKey.value -> Outcome.InvalidKey
        else -> Outcome.Failed
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
}
