package com.manhwaviewer.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class GoogleTranslateRequest(
    val q: List<String>,
    val source: String,
    val target: String,
    val format: String,
)

@Serializable
private data class GoogleTranslateResponse(
    val data: GoogleTranslateData,
)

@Serializable
private data class GoogleTranslateData(
    val translations: List<GoogleTranslation>,
)

@Serializable
private data class GoogleTranslation(
    val translatedText: String,
)

class GoogleTranslationService(
    private val apiKey: String,
    engine: HttpClientEngine? = null,
) : TranslationService {

    override val engineName = "GOOGLE_CLOUD"

    private val httpClient: HttpClient by lazy {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }

    companion object {
        private const val API_URL = "https://translation.googleapis.com/language/translate/v2"

        // v2 API 는 요청 하나에 q 를 128개까지만 받는다.
        private const val MAX_TEXTS_PER_REQUEST = 128

        // 기본값 html 은 번역 결과를 HTML 엔티티로 이스케이프해 돌려준다.
        private const val FORMAT_TEXT = "text"
    }

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> =
        texts.chunked(MAX_TEXTS_PER_REQUEST).flatMap { translateChunk(it, source, target) }

    private suspend fun translateChunk(texts: List<String>, source: String, target: String): List<String> {
        val httpResponse = httpClient.post(API_URL) {
            // 키를 쿼리 파라미터로 보내면 URL 과 함께 로그·프록시에 남는다.
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(GoogleTranslateRequest(texts, source, target, FORMAT_TEXT))
        }

        if (httpResponse.status.value !in 200..299) {
            val errorBody = httpResponse.bodyAsText()
            throw RuntimeException("Google Translation API error ${httpResponse.status.value}: $errorBody")
        }

        val response: GoogleTranslateResponse = httpResponse.body()
        val translated = response.data.translations.map { it.translatedText }
        check(translated.size == texts.size) {
            "Google Translation API returned ${translated.size} translations for ${texts.size} texts"
        }
        return translated
    }
}
