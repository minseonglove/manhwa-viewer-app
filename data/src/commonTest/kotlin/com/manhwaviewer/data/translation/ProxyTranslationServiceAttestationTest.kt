package com.manhwaviewer.data.translation

import com.manhwaviewer.data.translation.attestation.AttestationProof
import com.manhwaviewer.data.translation.attestation.AttestationResult
import com.manhwaviewer.data.translation.attestation.DeviceAttestor
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProxyTranslationServiceAttestationTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val sessions = mutableListOf<JsonObject>()
    private val translateAuthorizations = mutableListOf<String?>()
    private var challenges = 0
    private var sessionReply = """{"token":"tok","tier":"attested","expiresIn":43200}"""

    private class FakeAttestor(
        var result: AttestationResult,
        private val proveThrows: Throwable? = null,
        private val onCredentialThrows: Throwable? = null,
        private val onCredentialRejectedThrows: Throwable? = null,
    ) : DeviceAttestor {
        val proveCalls = mutableListOf<Pair<String, String>>()
        val credentials = mutableListOf<Pair<String, Long?>>()
        var rejected = 0
        override suspend fun prove(challenge: String, deviceId: String): AttestationResult {
            proveCalls += challenge to deviceId
            proveThrows?.let { throw it }
            return result
        }
        override suspend fun onCredential(credential: String, recheckAfterSeconds: Long?) {
            credentials += credential to recheckAfterSeconds
            onCredentialThrows?.let { throw it }
        }
        override suspend fun onCredentialRejected() {
            rejected++
            onCredentialRejectedThrows?.let { throw it }
        }
    }

    private fun MockRequestHandleScope.ok(body: String): HttpResponseData = respond(body, HttpStatusCode.OK, jsonHeaders)

    private fun service(attestor: DeviceAttestor?, devSecret: String? = null, clock: () -> Long = { 1000 }, challengeFails: Boolean = false) =
        ProxyTranslationService(
            baseUrl = "https://proxy.test",
            deviceId = { "device-1" },
            devSecret = devSecret,
            attestor = attestor,
            clock = clock,
            engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/v1/challenge" -> if (challengeFails) respond("", HttpStatusCode.InternalServerError) else ok("""{"challenge":"ch-${++challenges}","expiresIn":300}""")
                    "/v1/session" -> {
                        sessions += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                        ok(sessionReply)
                    }
                    else -> {
                        translateAuthorizations += request.headers[HttpHeaders.Authorization]
                        ok("""{"translations":{"t000":"번역"},"engine":"gemma"}""")
                    }
                }
            },
        )

    @Test
    fun sendsTheProofForAFreshChallenge() = runTest {
        val attestor = FakeAttestor(AttestationResult.Proof(AttestationProof(platform = "android", credential = "c", signature = "s")))
        service(attestor).translate(listOf("は"), "ja", "ko")

        assertEquals(listOf("ch-1" to "device-1"), attestor.proveCalls)
        val body = sessions.single()
        assertEquals("device-1", body.getValue("deviceId").jsonPrimitive.content)
        assertEquals("ch-1", body.getValue("challenge").jsonPrimitive.content)
        assertEquals("android", body.getValue("platform").jsonPrimitive.content)
        assertEquals("c", body.getValue("credential").jsonPrimitive.content)
        assertEquals("s", body.getValue("signature").jsonPrimitive.content)
        assertFalse("secret" in body)
    }

    @Test
    fun handsNewCredentialsAndRejectionsToTheAttestor() = runTest {
        val attestor = FakeAttestor(AttestationResult.Proof(AttestationProof(platform = "android", publicKey = "pk", integrityToken = "it")))
        sessionReply = """{"token":"tok","tier":"attested","expiresIn":43200,"credential":"new-cred","recheckAfter":604800}"""
        service(attestor).translate(listOf("は"), "ja", "ko")
        assertEquals<List<Pair<String, Long?>>>(listOf("new-cred" to 604800L), attestor.credentials)

        sessionReply = """{"token":"tok","tier":"unattested","expiresIn":43200,"credentialStatus":"invalid"}"""
        service(attestor).translate(listOf("は"), "ja", "ko")
        assertEquals(1, attestor.rejected)
    }

    @Test
    fun fallsBackToTheDevelopmentSecretOrNothingWhenTheDeviceCannotAttest() = runTest {
        service(FakeAttestor(AttestationResult.Unavailable), devSecret = "dev").translate(listOf("は"), "ja", "ko")
        assertEquals("dev", sessions.last().getValue("platform").jsonPrimitive.content)
        assertEquals("dev", sessions.last().getValue("secret").jsonPrimitive.content)

        service(FakeAttestor(AttestationResult.Unavailable)).translate(listOf("は"), "ja", "ko")
        assertEquals(setOf("deviceId"), sessions.last().keys)
    }

    @Test
    fun opensANewSessionTenMinutesAfterATransientFailure() = runTest {
        var now = 1000L
        val attestor = FakeAttestor(AttestationResult.TransientFailure)
        sessionReply = """{"token":"tok","tier":"unattested","expiresIn":43200}"""
        val proxy = service(attestor, clock = { now })

        proxy.translate(listOf("は"), "ja", "ko")
        now += 599
        proxy.translate(listOf("は"), "ja", "ko")
        assertEquals(1, sessions.size)

        now += 1
        attestor.result = AttestationResult.Proof(AttestationProof(platform = "android", credential = "c", signature = "s"))
        sessionReply = """{"token":"tok2","tier":"attested","expiresIn":43200}"""
        proxy.translate(listOf("は"), "ja", "ko")
        assertEquals(2, sessions.size)
        assertEquals("c", sessions.last().getValue("credential").jsonPrimitive.content)
    }

    @Test
    fun keepsTranslatingWhenTheAttestorFailsToStoreACredential() = runTest {
        val attestor = FakeAttestor(
            AttestationResult.Proof(AttestationProof(platform = "android", credential = "c", signature = "s")),
            onCredentialThrows = IllegalStateException("disk full"),
        )
        sessionReply = """{"token":"tok","tier":"attested","expiresIn":43200,"credential":"new-cred","recheckAfter":604800}"""

        val result = service(attestor).translate(listOf("は"), "ja", "ko")

        assertEquals(listOf("번역"), result)
        assertEquals("Bearer tok", translateAuthorizations.last())
    }

    @Test
    fun keepsTranslatingWhenTheAttestorFailsToRecordARejection() = runTest {
        val attestor = FakeAttestor(
            AttestationResult.Proof(AttestationProof(platform = "android", credential = "c", signature = "s")),
            onCredentialRejectedThrows = IllegalStateException("disk full"),
        )
        sessionReply = """{"token":"tok","tier":"unattested","expiresIn":43200,"credentialStatus":"invalid"}"""

        val result = service(attestor).translate(listOf("は"), "ja", "ko")

        assertEquals(listOf("번역"), result)
        assertEquals("Bearer tok", translateAuthorizations.last())
    }

    @Test
    fun opensANewSessionTenMinutesAfterAThrowingProve() = runTest {
        var now = 1000L
        val attestor = FakeAttestor(AttestationResult.Unavailable, proveThrows = IllegalStateException("keystore error"))
        sessionReply = """{"token":"tok","tier":"unattested","expiresIn":43200}"""
        val proxy = service(attestor, clock = { now })

        val result = proxy.translate(listOf("は"), "ja", "ko")
        assertEquals(listOf("번역"), result)
        assertEquals(setOf("deviceId"), sessions.single().keys)

        now += 599
        proxy.translate(listOf("は"), "ja", "ko")
        assertEquals(1, sessions.size)

        now += 1
        proxy.translate(listOf("は"), "ja", "ko")
        assertEquals(2, sessions.size)
    }

    @Test
    fun keepsTranslatingWhenTheChallengeCannotBeFetched() = runTest {
        val attestor = FakeAttestor(AttestationResult.Unavailable)
        val result = service(attestor, challengeFails = true).translate(listOf("は"), "ja", "ko")
        assertEquals(listOf("번역"), result)
        assertEquals(emptyList(), attestor.proveCalls)
        assertNull(sessions.single()["challenge"])
    }
}
