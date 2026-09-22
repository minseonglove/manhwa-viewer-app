package com.manhwaviewer.data.translation.attestation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.manhwaviewer.data.preference.AttestationStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives [PlayIntegrityAttestor]'s recovery and recheck logic with fakes, through the internal constructor, without touching real Keystore or Play Integrity. */
class PlayIntegrityAttestorTest {

    private class InMemoryDataStore : DataStore<Preferences> {
        var failWrites = false
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("disk full")
            return transform(state.value).also { state.value = it }
        }
    }

    private class FakeKey(
        override val publicKeySpki: ByteArray,
        override val hardwareBacked: Boolean,
        private val onSign: (() -> ByteArray)? = null,
    ) : SessionKeyLike {
        override fun sign(data: ByteArray): ByteArray = onSign?.invoke() ?: data
    }

    private fun proofOf(result: AttestationResult): AttestationProof = (result as AttestationResult.Proof).proof

    private fun requestHashFor(challenge: String, deviceId: String, key: SessionKeyLike): String =
        SessionBinding.base64Url(SessionBinding.compute(challenge, deviceId, SessionBinding.base64Url(key.publicKeySpki)))

    @Test
    fun signFailureDeletesTheAliasClearsTheCredentialAndRegistersTheNewKeyInstead() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        store.saveCredential("stale-cred", recheckAtEpochSeconds = 9_999)
        val brokenKey = FakeKey(byteArrayOf(1), hardwareBacked = true, onSign = { error("key blob is invalid") })
        val freshKey = FakeKey(byteArrayOf(2), hardwareBacked = true)
        var keyCalls = 0
        var deleted = false
        val requestHashes = mutableListOf<String>()
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 },
            sessionKey = { keyCalls++; if (keyCalls == 1) brokenKey else freshKey },
            deleteSessionKey = { deleted = true },
            requestToken = { requestHashes += it; TokenResult.Token("fresh-token") },
        )

        val result = attestor.prove("challenge", "device")

        assertTrue(deleted, "the broken alias must be deleted")
        assertNull(store.read().credential, "the stale credential must be cleared")
        val proof = proofOf(result)
        assertEquals("fresh-token", proof.integrityToken)
        assertEquals(SessionBinding.base64Url(freshKey.publicKeySpki), proof.publicKey)
        assertNull(proof.credential, "a fresh key registers with no credential")
        assertNull(proof.signature, "a fresh key registers with no signature")
        assertEquals(listOf(requestHashFor("challenge", "device", freshKey)), requestHashes, "Play Integrity must vouch for the new key's binding")
    }

    @Test
    fun registrationAsksPlayIntegrityToVouchForTheBindingOfThePublicKeyItSends() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        val key = FakeKey(byteArrayOf(7, 8, 9), hardwareBacked = true)
        val requestHashes = mutableListOf<String>()
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 },
            sessionKey = { key },
            deleteSessionKey = { },
            requestToken = { requestHashes += it; TokenResult.Token("token") },
        )

        val proof = proofOf(attestor.prove("challenge", "device"))

        assertEquals(SessionBinding.base64Url(key.publicKeySpki), proof.publicKey)
        assertEquals(listOf(requestHashFor("challenge", "device", key)), requestHashes)
        assertEquals(listOf(SessionBinding.base64Url(SessionBinding.compute("challenge", "device", proof.publicKey!!))), requestHashes)
    }

    @Test
    fun aFailedCredentialClearKeepsTheBrokenKeySoTheNextProveRecoversAgain() = runTest {
        val dataStore = InMemoryDataStore()
        val store = AttestationStateStore(dataStore)
        store.saveCredential("stale-cred", recheckAtEpochSeconds = 9_999)
        val brokenKey = FakeKey(byteArrayOf(1), hardwareBacked = true, onSign = { error("key blob is invalid") })
        val freshKey = FakeKey(byteArrayOf(2), hardwareBacked = true)
        var deleted = false
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 },
            sessionKey = { if (deleted) freshKey else brokenKey },
            deleteSessionKey = { deleted = true },
            requestToken = { TokenResult.Token("fresh-token") },
        )

        dataStore.failWrites = true
        try {
            attestor.prove("challenge", "device")
        } catch (e: IOException) {
            // Whether the storage failure surfaces is not what this test is about; what is left behind is.
        }

        assertFalse(deleted, "the broken alias must stay while the stale credential is still stored")
        assertEquals("stale-cred", store.read().credential)

        dataStore.failWrites = false
        val proof = proofOf(attestor.prove("challenge", "device"))

        assertTrue(deleted)
        assertNull(store.read().credential)
        assertEquals(SessionBinding.base64Url(freshKey.publicKeySpki), proof.publicKey)
        assertNull(proof.credential)
    }

    @Test
    fun aKeyThatNeverRecoversReportsUnavailableWithoutThrowing() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 },
            sessionKey = { error("keystore is gone") },
            deleteSessionKey = { },
            requestToken = { error("must not be called: no key means no proof to send") },
        )

        val result = attestor.prove("challenge", "device")

        assertEquals(AttestationResult.Unavailable, result)
    }

    @Test
    fun aTransientTokenFailureAfterTheRecheckIsDueIsNeverMaskedByTheStaleSignature() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        store.saveCredential("cred", recheckAtEpochSeconds = 500)
        val key = FakeKey(byteArrayOf(3), hardwareBacked = true)
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 }, // past recheckAtEpochSeconds, so the recheck is due
            sessionKey = { key },
            deleteSessionKey = { },
            requestToken = { TokenResult.Failed(AttestationResult.TransientFailure) },
        )

        val result = attestor.prove("challenge", "device")

        assertEquals(AttestationResult.TransientFailure, result)
    }

    @Test
    fun beforeTheRecheckIsDueTheStoredSignatureIsEnoughAndNoTokenIsRequested() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        store.saveCredential("cred", recheckAtEpochSeconds = 5_000)
        val key = FakeKey(byteArrayOf(4), hardwareBacked = true)
        var tokenCalls = 0
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 }, // before recheckAtEpochSeconds
            sessionKey = { key },
            deleteSessionKey = { },
            requestToken = { tokenCalls++; TokenResult.Token("must-not-be-used") },
        )

        val result = attestor.prove("challenge", "device")

        assertEquals(0, tokenCalls, "a signature within the recheck window must not call Play Integrity")
        val proof = proofOf(result)
        assertEquals("cred", proof.credential)
        assertNull(proof.integrityToken)
    }

    @Test
    fun onCredentialDoesNotStoreForANonHardwareBackedKey() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        val key = FakeKey(byteArrayOf(5), hardwareBacked = false)
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 },
            sessionKey = { key },
            deleteSessionKey = { },
            requestToken = { error("must not be called") },
        )

        attestor.onCredential("new-cred", recheckAfterSeconds = 3600)

        assertNull(store.read().credential)
    }

    @Test
    fun onCredentialSchedulesTheRecheckAnHourBeforeTheProxysCutoffButNeverInThePast() = runTest {
        val store = AttestationStateStore(InMemoryDataStore())
        val attestor = PlayIntegrityAttestor(
            cloudProjectNumber = 42,
            store = store,
            devSecret = null,
            clock = { 1_000 },
            sessionKey = { FakeKey(byteArrayOf(6), hardwareBacked = true) },
            deleteSessionKey = { },
            requestToken = { error("must not be called") },
        )

        attestor.onCredential("cred", recheckAfterSeconds = 604_800)
        assertEquals(1_000L + 604_800 - 3_600, store.read().recheckAtEpochSeconds)

        attestor.onCredential("cred", recheckAfterSeconds = 60)
        assertEquals(1_000L, store.read().recheckAtEpochSeconds)
    }
}
