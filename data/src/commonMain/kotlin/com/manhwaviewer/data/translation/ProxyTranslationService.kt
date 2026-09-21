package com.manhwaviewer.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ProxySessionRequest(
    val deviceId: String,
    val platform: String? = null,
    val secret: String? = null,
)

@Serializable
private data class ProxySessionResponse(val token: String)

@Serializable
private data class ProxyItem(val id: String, val kind: String, val text: String)

@Serializable
private data class ProxyGlossaryEntry(val src: String, val ko: String)

@Serializable
private data class ProxyTranslateRequest(
    val items: List<ProxyItem>,
    val source: String,
    val target: String,
    val glossary: List<ProxyGlossaryEntry> = emptyList(),
)

@Serializable
private data class ProxyTranslateResponse(val translations: Map<String, String>)

/**
 * Translation through the app's own proxy, which holds the provider keys and picks the engine.
 *
 * [devSecret] is the development stand-in for platform attestation and must stay out of release builds:
 * without it the proxy still answers, from its unattested tier.
 */
class ProxyTranslationService(
    private val baseUrl: String,
    private val deviceId: suspend () -> String,
    private val devSecret: String? = null,
    engine: HttpClientEngine? = null,
) : TranslationService {

    override val engineName = "PROXY"

    private val httpClient: HttpClient by lazy {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; explicitNulls = false })
            }
        }
        if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }

    private val sessionLock = Mutex()
    private var token: String? = null

    companion object {
        // The proxy rejects anything larger outright (proxy/src/limits.ts).
        private const val MAX_ITEMS = 64
        private const val MAX_ITEM_CHARS = 400
        private const val MAX_TOTAL_CHARS = 4000

        // The app does not classify regions, and the prompt treats an unmarked line as dialogue.
        private const val DEFAULT_KIND = "bubble"
    }

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> =
        chunk(texts).flatMap { translateChunk(it, source, target) }

    private fun chunk(texts: List<String>): List<List<String>> {
        val chunks = mutableListOf<MutableList<String>>()
        var characters = 0
        for (text in texts) {
            require(text.length <= MAX_ITEM_CHARS) { "a text of ${text.length} characters exceeds the proxy limit" }
            val current = chunks.lastOrNull()
            if (current == null || current.size == MAX_ITEMS || characters + text.length > MAX_TOTAL_CHARS) {
                chunks += mutableListOf(text)
                characters = text.length
            } else {
                current += text
                characters += text.length
            }
        }
        return chunks
    }

    private suspend fun translateChunk(texts: List<String>, source: String, target: String): List<String> {
        val items = texts.mapIndexed { index, text -> ProxyItem("t${index.toString().padStart(3, '0')}", DEFAULT_KIND, text) }
        val request = ProxyTranslateRequest(items, source, target)

        var response = post(request, session(renew = false))
        if (response.status == HttpStatusCode.Unauthorized) {
            response = post(request, session(renew = true))
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("Translation proxy error ${response.status.value}")
        }

        val translations = response.body<ProxyTranslateResponse>().translations
        return items.map { item ->
            translations[item.id] ?: throw RuntimeException("Translation proxy returned no text for ${item.id}")
        }
    }

    private suspend fun post(request: ProxyTranslateRequest, sessionToken: String): HttpResponse =
        httpClient.post("$baseUrl/v1/translate") {
            bearerAuth(sessionToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private suspend fun session(renew: Boolean): String = sessionLock.withLock {
        if (renew) token = null
        token ?: openSession().also { token = it }
    }

    private suspend fun openSession(): String {
        val response = httpClient.post("$baseUrl/v1/session") {
            contentType(ContentType.Application.Json)
            val id = deviceId()
            setBody(
                if (devSecret != null) ProxySessionRequest(id, platform = "dev", secret = devSecret)
                else ProxySessionRequest(id),
            )
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("Translation proxy session error ${response.status.value}")
        }
        return response.body<ProxySessionResponse>().token
    }
}
