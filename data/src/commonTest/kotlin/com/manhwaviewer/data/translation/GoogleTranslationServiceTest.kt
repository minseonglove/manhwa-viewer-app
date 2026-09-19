package com.manhwaviewer.data.translation

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GoogleTranslationServiceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun MockRequestHandleScope.respondTranslations(vararg translated: String): HttpResponseData {
        val items = translated.joinToString(",") { """{"translatedText":"$it"}""" }
        return respond("""{"data":{"translations":[$items]}}""", HttpStatusCode.OK, jsonHeaders)
    }

    private suspend fun HttpRequestData.qTexts(): List<String> =
        Json.parseToJsonElement(body.toByteArray().decodeToString())
            .jsonObject.getValue("q").jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun sendsEachTextAsSeparateEntryInJsonBody() = runTest {
        var captured: HttpRequestData? = null
        val service = GoogleTranslationService(
            apiKey = "test-key",
            engine = MockEngine { request ->
                captured = request
                respondTranslations("가", "나")
            },
        )

        service.translate(listOf("一行目\n二行目", "b"), source = "ja", target = "ko")

        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://translation.googleapis.com/language/translate/v2", request.url.toString())
        val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
        assertEquals(listOf("一行目\n二行目", "b"), request.qTexts())
        assertEquals("ja", body.getValue("source").jsonPrimitive.content)
        assertEquals("ko", body.getValue("target").jsonPrimitive.content)
        assertEquals("text", body.getValue("format").jsonPrimitive.content)
    }

    @Test
    fun sendsApiKeyInHeaderAndNeverInUrl() = runTest {
        var captured: HttpRequestData? = null
        val service = GoogleTranslationService(
            apiKey = "test-key",
            engine = MockEngine { request ->
                captured = request
                respondTranslations("가")
            },
        )

        service.translate(listOf("a"), source = "ja", target = "ko")

        val request = captured!!
        assertEquals("test-key", request.headers["x-goog-api-key"])
        assertNull(request.url.parameters["key"])
    }

    @Test
    fun returnsTranslationsInRequestOrder() = runTest {
        val service = GoogleTranslationService(
            apiKey = "test-key",
            engine = MockEngine { respondTranslations("첫째", "둘째", "셋째") },
        )

        val result = service.translate(listOf("a", "b", "c"), source = "ja", target = "ko")

        assertEquals(listOf("첫째", "둘째", "셋째"), result)
    }

    @Test
    fun throwsWhenTranslationCountDiffersFromRequest() = runTest {
        val service = GoogleTranslationService(
            apiKey = "test-key",
            engine = MockEngine { respondTranslations("하나뿐") },
        )

        assertFailsWith<IllegalStateException> {
            service.translate(listOf("a", "b"), source = "ja", target = "ko")
        }
    }

    @Test
    fun throwsOnHttpError() = runTest {
        val service = GoogleTranslationService(
            apiKey = "test-key",
            engine = MockEngine {
                respond(
                    """{"error":{"code":403,"message":"API key not valid","status":"PERMISSION_DENIED"}}""",
                    HttpStatusCode.Forbidden,
                    jsonHeaders,
                )
            },
        )

        assertFailsWith<RuntimeException> {
            service.translate(listOf("a"), source = "ja", target = "ko")
        }
    }

    @Test
    fun splitsRequestsAtApiLimitOf128Texts() = runTest {
        val requestSizes = mutableListOf<Int>()
        val service = GoogleTranslationService(
            apiKey = "test-key",
            engine = MockEngine { request ->
                val texts = request.qTexts()
                requestSizes += texts.size
                respondTranslations(*texts.map { "번역:$it" }.toTypedArray())
            },
        )
        val texts = (1..129).map { "t$it" }

        val result = service.translate(texts, source = "ja", target = "ko")

        assertEquals(listOf(128, 1), requestSizes)
        assertEquals("번역:t1", result.first())
        assertEquals("번역:t128", result[127])
        assertEquals("번역:t129", result.last())
        assertEquals(129, result.size)
    }
}
