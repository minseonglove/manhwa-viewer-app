package com.manhwaviewer.data.translation

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyTranslationServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class Recorded(val path: String, val authorization: String?, val body: JsonObject)

    private val requests = mutableListOf<Recorded>()

    private suspend fun record(request: HttpRequestData): Recorded =
        Recorded(
            path = request.url.encodedPath,
            authorization = request.headers[HttpHeaders.Authorization],
            body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject,
        ).also { requests += it }

    private fun MockRequestHandleScope.session(token: String): HttpResponseData =
        respond("""{"token":"$token","tier":"attested","expiresIn":43200}""", HttpStatusCode.OK, jsonHeaders)

    /** Answers every item with its own text in brackets, so order and completeness are checkable. */
    private fun MockRequestHandleScope.echo(recorded: Recorded): HttpResponseData {
        val pairs = recorded.body.getValue("items").jsonArray.joinToString(",") {
            val item = it.jsonObject
            "\"${item.getValue("id").jsonPrimitive.content}\":\"[${item.getValue("text").jsonPrimitive.content}]\""
        }
        return respond("""{"translations":{$pairs},"engine":"gemma"}""", HttpStatusCode.OK, jsonHeaders)
    }

    private fun service(devSecret: String? = "dev-secret", handler: suspend MockRequestHandleScope.(Recorded) -> HttpResponseData) =
        ProxyTranslationService(
            baseUrl = "https://proxy.test",
            deviceId = { "device-1" },
            devSecret = devSecret,
            engine = MockEngine { request -> handler(record(request)) },
        )

    @Test
    fun opensASessionThenTranslatesWithItsToken() = runTest {
        val service = service { if (it.path == "/v1/session") session("tok-1") else echo(it) }

        val result = service.translate(listOf("あ", "い"), "ja", "ko")

        assertEquals(listOf("[あ]", "[い]"), result)
        assertEquals(listOf("/v1/session", "/v1/translate"), requests.map { it.path })
        assertEquals("Bearer tok-1", requests[1].authorization)
        val body = requests[1].body
        assertEquals("ja", body.getValue("source").jsonPrimitive.content)
        assertEquals("ko", body.getValue("target").jsonPrimitive.content)
        assertEquals(listOf("bubble", "bubble"), body.getValue("items").jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content })
    }

    @Test
    fun provesItselfWithTheDevSecretOnlyWhenOneIsConfigured() = runTest {
        service(devSecret = "s3cret") { if (it.path == "/v1/session") session("t") else echo(it) }.translate(listOf("あ"), "ja", "ko")
        val withSecret = requests.first().body
        requests.clear()
        service(devSecret = null) { if (it.path == "/v1/session") session("t") else echo(it) }.translate(listOf("あ"), "ja", "ko")
        val withoutSecret = requests.first().body

        assertEquals("device-1", withSecret.getValue("deviceId").jsonPrimitive.content)
        assertEquals("dev", withSecret.getValue("platform").jsonPrimitive.content)
        assertEquals("s3cret", withSecret.getValue("secret").jsonPrimitive.content)
        assertFalse("secret" in withoutSecret)
        assertFalse("platform" in withoutSecret)
    }

    @Test
    fun reusesTheSessionAcrossCalls() = runTest {
        val service = service { if (it.path == "/v1/session") session("tok-1") else echo(it) }

        service.translate(listOf("あ"), "ja", "ko")
        service.translate(listOf("い"), "ja", "ko")

        assertEquals(1, requests.count { it.path == "/v1/session" })
    }

    @Test
    fun opensANewSessionOnceWhenTheTokenIsRejected() = runTest {
        var sessions = 0
        val service = service {
            when {
                it.path == "/v1/session" -> session("tok-${++sessions}")
                it.authorization == "Bearer tok-1" -> respond("""{"error":"a valid session token is required"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                else -> echo(it)
            }
        }

        val result = service.translate(listOf("あ"), "ja", "ko")

        assertEquals(listOf("[あ]"), result)
        assertEquals(listOf("/v1/session", "/v1/translate", "/v1/session", "/v1/translate"), requests.map { it.path })
    }

    @Test
    fun callsRejectedTogetherShareOneNewSession() = runTest {
        val parallel = 3
        val counters = Mutex()
        var sessions = 0
        var rejected = 0
        val allRejected = CompletableDeferred<Unit>()
        val service = ProxyTranslationService(
            baseUrl = "https://proxy.test",
            deviceId = { "device-1" },
            devSecret = "dev-secret",
            engine = MockEngine { request ->
                when {
                    request.url.encodedPath == "/v1/session" -> session("tok-${counters.withLock { ++sessions }}")
                    request.headers[HttpHeaders.Authorization] == "Bearer tok-1" -> {
                        // Holds every rejection until all calls have been rejected, so they all ask for a new session with the same stale token.
                        if (counters.withLock { ++rejected } == parallel) allRejected.complete(Unit)
                        allRejected.await()
                        respond("""{"error":"a valid session token is required"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                    }
                    else -> respond("""{"translations":{"t000":"번역"},"engine":"gemma"}""", HttpStatusCode.OK, jsonHeaders)
                }
            },
        )

        val results = List(parallel) { async { service.translate(listOf("あ"), "ja", "ko") } }.awaitAll()

        assertEquals(List(parallel) { listOf("번역") }, results)
        assertEquals(2, counters.withLock { sessions }, "one session before the rejection and one shared by every call after it")
    }

    @Test
    fun splitsARequestThatExceedsTheProxyLimits() = runTest {
        val service = service { if (it.path == "/v1/session") session("t") else echo(it) }
        val texts = List(70) { "あ$it" }

        val result = service.translate(texts, "ja", "ko")

        assertEquals(texts.map { "[$it]" }, result)
        val sizes = requests.filter { it.path == "/v1/translate" }.map { it.body.getValue("items").jsonArray.size }
        assertEquals(listOf(64, 6), sizes)
    }

    @Test
    fun splitsByTotalCharactersToo() = runTest {
        val service = service { if (it.path == "/v1/session") session("t") else echo(it) }
        val texts = List(12) { "あ".repeat(390) }

        service.translate(texts, "ja", "ko")

        val totals = requests.filter { it.path == "/v1/translate" }
            .map { r -> r.body.getValue("items").jsonArray.sumOf { it.jsonObject.getValue("text").jsonPrimitive.content.length } }
        assertTrue(totals.all { it <= 4000 }, "chunks of $totals characters")
        assertEquals(12 * 390, totals.sum())
    }

    @Test
    fun failsWhenTheProxyCannotTranslate() = runTest {
        val service = service {
            if (it.path == "/v1/session") session("t")
            else respond("""{"error":"all translation engines failed"}""", HttpStatusCode.BadGateway, jsonHeaders)
        }

        assertFailsWith<RuntimeException> { service.translate(listOf("あ"), "ja", "ko") }
        assertEquals(1, requests.count { it.path == "/v1/translate" })
    }

    @Test
    fun failsWhenAnAnswerIsMissing() = runTest {
        val service = service {
            if (it.path == "/v1/session") session("t")
            else respond("""{"translations":{"t000":"가"},"engine":"gemma"}""", HttpStatusCode.OK, jsonHeaders)
        }

        assertFailsWith<RuntimeException> { service.translate(listOf("あ", "い"), "ja", "ko") }
    }
}
