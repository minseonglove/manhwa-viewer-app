package com.manhwaviewer.data.translation.attestation

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityException
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.model.StandardIntegrityErrorCode
import com.manhwaviewer.data.preference.AttestationStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun classifyIntegrityError(errorCode: Int): AttestationResult = when (errorCode) {
    StandardIntegrityErrorCode.API_NOT_AVAILABLE,
    StandardIntegrityErrorCode.PLAY_STORE_NOT_FOUND,
    StandardIntegrityErrorCode.PLAY_SERVICES_NOT_FOUND,
    StandardIntegrityErrorCode.CANNOT_BIND_TO_SERVICE,
    StandardIntegrityErrorCode.PLAY_STORE_VERSION_OUTDATED,
    StandardIntegrityErrorCode.PLAY_SERVICES_VERSION_OUTDATED,
    StandardIntegrityErrorCode.CLOUD_PROJECT_NUMBER_IS_INVALID,
    -> AttestationResult.Unavailable
    else -> AttestationResult.TransientFailure
}

/** What [PlayIntegrityAttestor] needs from the install's signing key. Lets a JVM unit test drive the attestor's decision logic with a fake instead of real Keystore. */
internal interface SessionKeyLike {
    val publicKeySpki: ByteArray
    val hardwareBacked: Boolean
    fun sign(data: ByteArray): ByteArray
}

/** Outcome of a Play Integrity token request, exposed so a unit test can fake [PlayIntegrityAttestor]'s token source. */
internal sealed interface TokenResult {
    data class Token(val value: String) : TokenResult
    data class Failed(val result: AttestationResult) : TokenResult
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}

/** Owns the `StandardIntegrityTokenProvider` Google hands out per `prepareIntegrityToken` call, and retries once if it goes stale. */
private class PlayIntegrityTokenSource(
    private val context: Context,
    private val cloudProjectNumber: Long,
) {
    private val providerLock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    private suspend fun tokenProvider(): StandardIntegrityTokenProvider = providerLock.withLock {
        provider ?: IntegrityManagerFactory.createStandard(context)
            .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build())
            .await()
            .also { provider = it }
    }

    suspend fun request(requestHash: String): TokenResult = requestRetrying(requestHash, retried = false)

    private suspend fun requestRetrying(requestHash: String, retried: Boolean): TokenResult =
        try {
            val request = StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()
            TokenResult.Token(tokenProvider().request(request).await().token())
        } catch (e: CancellationException) {
            throw e
        } catch (e: StandardIntegrityException) {
            if (e.errorCode == StandardIntegrityErrorCode.INTEGRITY_TOKEN_PROVIDER_INVALID && !retried) {
                providerLock.withLock { provider = null }
                requestRetrying(requestHash, retried = true)
            } else {
                TokenResult.Failed(classifyIntegrityError(e.errorCode))
            }
        } catch (e: Exception) {
            TokenResult.Failed(AttestationResult.TransientFailure)
        }
}

/**
 * Registers the install's Keystore key with the proxy through Play Integrity (standard requests), then proves each session
 * with a signature alone until the proxy asks for a recheck. A key outside secure hardware is never trusted with a credential:
 * such an install sends a fresh Play Integrity token every session.
 *
 * [sessionKey], [deleteSessionKey] and [requestToken] are the real Keystore/Play-Integrity calls in production; the internal
 * constructor lets a JVM unit test replace them with fakes to drive the recovery and recheck logic without a device.
 */
class PlayIntegrityAttestor internal constructor(
    private val cloudProjectNumber: Long,
    private val store: AttestationStateStore,
    private val devSecret: String?,
    private val clock: () -> Long,
    private val sessionKey: () -> SessionKeyLike,
    private val deleteSessionKey: () -> Unit,
    private val requestToken: suspend (String) -> TokenResult,
) : DeviceAttestor {

    constructor(
        context: Context,
        cloudProjectNumber: Long,
        store: AttestationStateStore,
        devSecret: String?,
        clock: () -> Long = { System.currentTimeMillis() / 1000 },
    ) : this(
        cloudProjectNumber = cloudProjectNumber,
        store = store,
        devSecret = devSecret,
        clock = clock,
        sessionKey = { KeystoreSessionKey.getOrCreate() },
        deleteSessionKey = { KeystoreSessionKey.delete() },
        requestToken = PlayIntegrityTokenSource(context, cloudProjectNumber)::request,
    )

    companion object {
        // Preparing the provider takes a few seconds and Google suggests a generous timeout; past this the session goes unattested for now.
        private const val PROVE_TIMEOUT_MS = 10_000L

        // The proxy counts seven days from when it issued the credential, which is a little before this device received it.
        private const val RECHECK_MARGIN_SECONDS = 3_600L
    }

    override suspend fun prove(challenge: String, deviceId: String): AttestationResult =
        withTimeoutOrNull(PROVE_TIMEOUT_MS) { proveNow(challenge, deviceId) } ?: AttestationResult.TransientFailure

    private suspend fun proveNow(challenge: String, deviceId: String): AttestationResult {
        var key = loadKey() ?: return AttestationResult.Unavailable
        var publicKey = SessionBinding.base64Url(key.publicKeySpki)
        var binding = SessionBinding.compute(challenge, deviceId, publicKey)

        val state = store.read()
        var credential = state.credential?.takeIf { key.hardwareBacked }
        var signature: String? = null

        if (credential != null) {
            try {
                signature = SessionBinding.base64Url(key.sign(binding))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The registered key can no longer sign (e.g. a bootloader/root-of-trust change invalidated the blob); start over as a fresh install.
                key = recoverSessionKey() ?: return AttestationResult.Unavailable
                publicKey = SessionBinding.base64Url(key.publicKeySpki)
                binding = SessionBinding.compute(challenge, deviceId, publicKey)
                credential = null
            }
        }

        val signed = credential?.let { AttestationResult.Proof(AttestationProof(platform = "android", credential = it, signature = signature)) }
        if (signed != null && clock() < state.recheckAtEpochSeconds) return signed

        if (cloudProjectNumber == 0L) {
            if (devSecret != null) {
                return AttestationResult.Proof(AttestationProof(platform = "android", publicKey = publicKey, devSecret = devSecret, credential = credential, signature = signature))
            }
            return signed ?: AttestationResult.Unavailable
        }
        return when (val token = requestToken(SessionBinding.base64Url(binding))) {
            is TokenResult.Token -> AttestationResult.Proof(
                AttestationProof(platform = "android", publicKey = publicKey, integrityToken = token.value, credential = credential, signature = signature),
            )
            is TokenResult.Failed -> token.result
        }
    }

    /** Loads the session key, and if it is unusable, deletes the alias and retries once with a freshly generated one. */
    private suspend fun loadKey(): SessionKeyLike? =
        try {
            sessionKey()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recoverSessionKey()
        }

    private suspend fun recoverSessionKey(): SessionKeyLike? {
        // The credential goes before the key: if clearing fails, the broken key stays and the next prove recovers again, instead of a new key being paired with the old credential.
        store.clearCredential()
        deleteSessionKey()
        return try {
            sessionKey()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun onCredential(credential: String, recheckAfterSeconds: Long?) {
        val hardwareBacked = try {
            sessionKey().hardwareBacked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (hardwareBacked) {
            val now = clock()
            store.saveCredential(credential, maxOf(now, now + (recheckAfterSeconds ?: 0) - RECHECK_MARGIN_SECONDS))
        }
    }

    override suspend fun onCredentialRejected() {
        store.clearCredential()
    }
}
