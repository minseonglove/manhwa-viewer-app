package com.manhwaviewer.data.translation

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PapagoResponse(
    val message: PapagoMessage,
)

@Serializable
data class PapagoMessage(
    val result: PapagoResult,
)

@Serializable
data class PapagoResult(
    val srcLangType: String,
    val tarLangType: String,
    val translatedText: String,
)

open class PapagoTranslationService(
    private val clientId: String,
    private val clientSecret: String,
) {
    private val httpClient: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    companion object {
        private const val API_URL = "https://papago.apigw.ntruss.com/nmt/v1/translation"
    }

    open suspend fun translate(
        text: String,
        source: String = "ja",
        target: String = "ko",
    ): String {
        val httpResponse = httpClient.submitForm(
            url = API_URL,
            formParameters = parameters {
                append("source", source)
                append("target", target)
                append("text", text)
            },
        ) {
            header("X-NCP-APIGW-API-KEY-ID", clientId)
            header("X-NCP-APIGW-API-KEY", clientSecret)
        }

        if (httpResponse.status.value !in 200..299) {
            val errorBody = httpResponse.bodyAsText()
            throw RuntimeException("Papago API error ${httpResponse.status.value}: $errorBody")
        }

        val response: PapagoResponse = httpResponse.body()
        return response.message.result.translatedText
    }

    fun close() {
        httpClient.close()
    }
}
