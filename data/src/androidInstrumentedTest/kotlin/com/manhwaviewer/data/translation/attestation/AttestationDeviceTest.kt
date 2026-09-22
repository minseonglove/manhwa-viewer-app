package com.manhwaviewer.data.translation.attestation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.preference.AttestationStateStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class AttestationDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()

    @Test
    fun keystoreKeyLivesInSecureHardwareAndItsSignaturesVerify() {
        val key = KeystoreSessionKey.getOrCreate()
        assertTrue("expected a TEE or StrongBox key on this device", key.hardwareBacked)
        val data = "binding".encodeToByteArray()
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(key.publicKeySpki))
        val verified = Signature.getInstance("SHA256withECDSA").run { initVerify(publicKey); update(data); verify(key.sign(data)) }
        assertTrue(verified)
    }

    @Test
    fun withoutPlayIntegrityConfiguredTheDeviceQuietlyCannotAttest() = runBlocking {
        val store = AttestationStateStore(testDataStore("unconfigured"))
        val result = PlayIntegrityAttestor(context, cloudProjectNumber = 0, store = store, devSecret = null).prove("challenge", "device")
        assertEquals(AttestationResult.Unavailable, result)
    }

    @Test
    fun anInvalidCloudProjectNeverThrows() = runBlocking {
        val store = AttestationStateStore(testDataStore("invalid-project"))
        val result = PlayIntegrityAttestor(context, cloudProjectNumber = 1, store = store, devSecret = null).prove("challenge", "device")
        assertTrue("got $result", result == AttestationResult.Unavailable || result == AttestationResult.TransientFailure)
    }

    @Test
    fun registersThroughTheDevelopmentPathThenAttestsFromSignaturesAlone() = runBlocking {
        val proxyUrl = args.getString("proxyUrl")
        val secret = args.getString("proxySecret")
        assumeTrue("needs -e proxyUrl and -e proxySecret", proxyUrl != null && secret != null)
        val store = AttestationStateStore(testDataStore("e2e"))
        val attestor = PlayIntegrityAttestor(context, cloudProjectNumber = 0, store = store, devSecret = secret)
        val http = HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val deviceId = "attestation-device-test"

        suspend fun openSession(): JsonObject {
            val challenge = http.post("$proxyUrl/v1/challenge").body<JsonObject>().getValue("challenge").jsonPrimitive.content
            val proof = (attestor.prove(challenge, deviceId) as? AttestationResult.Proof)?.proof ?: error("expected a proof")
            val body = buildJsonObject {
                put("deviceId", deviceId); put("platform", proof.platform); put("challenge", challenge)
                proof.publicKey?.let { put("publicKey", it) }; proof.devSecret?.let { put("devSecret", it) }
                proof.credential?.let { put("credential", it) }; proof.signature?.let { put("signature", it) }
            }
            val reply = http.post("$proxyUrl/v1/session") { contentType(ContentType.Application.Json); setBody(body) }.body<JsonObject>()
            reply["credential"]?.jsonPrimitive?.content?.let { attestor.onCredential(it, reply["recheckAfter"]?.jsonPrimitive?.content?.toLong()) }
            return reply
        }

        val first = openSession()
        assertEquals("attested", first.getValue("tier").jsonPrimitive.content)
        assertNotNull(store.read().credential)

        val second = openSession()
        assertEquals("attested", second.getValue("tier").jsonPrimitive.content)
        assertNull("a signature session must not re-register", second["credential"])
    }

    private fun testDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath {
            context.cacheDir.resolve("attestation-test-$name-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }
}
