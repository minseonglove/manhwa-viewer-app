package com.manhwaviewer.data.translation

import com.manhwaviewer.data.translation.attestation.AttestationResult
import com.manhwaviewer.data.translation.attestation.DeviceAttestor
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
private data class ProxySessionRequest(
    val deviceId: String,
    val platform: String? = null,
    val secret: String? = null,
    val challenge: String? = null,
    val publicKey: String? = null,
    val integrityToken: String? = null,
    val devSecret: String? = null,
    val keyId: String? = null,
    val attestation: String? = null,
    val assertion: String? = null,
    val credential: String? = null,
    val signature: String? = null,
)

@Serializable
private data class ProxySessionResponse(
    val token: String,
    val tier: String? = null,
    val credential: String? = null,
    val credentialStatus: String? = null,
    val recheckAfter: Long? = null,
)

@Serializable
private data class ProxyChallengeResponse(val challenge: String)

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
 * [attestor] proves the install is genuine so the proxy grants its paid engines. [devSecret] is the development stand-in
 * for devices that cannot attest and must stay out of release builds. Without either, the proxy still answers from its unattested tier.
 */
class ProxyTranslationService(
    private val baseUrl: String,
    private val deviceId: suspend () -> String,
    private val devSecret: String? = null,
    private val attestor: DeviceAttestor? = null,
    engine: HttpClientEngine? = null,
    private val clock: () -> Long = { Clock.System.now().epochSeconds },
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
    private var renewAtEpochSeconds: Long? = null

    companion object {
        // The proxy rejects anything larger outright (proxy/src/limits.ts).
        private const val MAX_ITEMS = 64
        private const val MAX_ITEM_CHARS = 400
        private const val MAX_TOTAL_CHARS = 4000

        // The app does not classify regions, and the prompt treats an unmarked line as dialogue.
        private const val DEFAULT_KIND = "bubble"

        // A session opened while attestation was briefly unavailable is replaced this soon, so the device is not stuck on the free tier.
        private const val TRANSIENT_RETRY_SECONDS = 600L
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

        val sessionToken = session()
        var response = post(request, sessionToken)
        if (response.status == HttpStatusCode.Unauthorized) {
            response = post(request, session(rejected = sessionToken))
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

    /** [rejected] is the token the proxy just refused; it is replaced only if no concurrent call has replaced it already. */
    private suspend fun session(rejected: String? = null): String = sessionLock.withLock {
        val due = renewAtEpochSeconds?.let { clock() >= it } == true
        if (due || (rejected != null && rejected == token)) token = null
        token ?: openSession().also { token = it }
    }

    private suspend fun openSession(): String {
        val id = deviceId()
        var request = if (devSecret != null) ProxySessionRequest(id, platform = "dev", secret = devSecret) else ProxySessionRequest(id)
        var transient = false
        if (attestor != null) {
            val challenge = fetchChallenge()
            if (challenge == null) {
                transient = true
            } else {
                when (val result = safely(AttestationResult.TransientFailure) { attestor.prove(challenge, id) }) {
                    is AttestationResult.Proof -> request = result.proof.let {
                        ProxySessionRequest(
                            deviceId = id, platform = it.platform, challenge = challenge, publicKey = it.publicKey,
                            integrityToken = it.integrityToken, devSecret = it.devSecret, keyId = it.keyId,
                            attestation = it.attestation, assertion = it.assertion, credential = it.credential, signature = it.signature,
                        )
                    }
                    AttestationResult.Unavailable -> Unit
                    AttestationResult.TransientFailure -> transient = true
                }
            }
        }
        val response = httpClient.post("$baseUrl/v1/session") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            throw RuntimeException("Translation proxy session error ${response.status.value}")
        }
        val body = response.body<ProxySessionResponse>()
        if (attestor != null) {
            body.credential?.let { credential -> safely(Unit) { attestor.onCredential(credential, body.recheckAfter) } }
            if (body.credentialStatus == "invalid") safely(Unit) { attestor.onCredentialRejected() }
        }
        renewAtEpochSeconds = if (transient && body.tier != "attested") clock() + TRANSIENT_RETRY_SECONDS else null
        return body.token
    }

    /** A proxy that cannot hand out challenges must not stop translation; the session just goes unattested. */
    private suspend fun fetchChallenge(): String? =
        safely(null) {
            val response = httpClient.post("$baseUrl/v1/challenge")
            if (response.status.value in 200..299) response.body<ProxyChallengeResponse>().challenge else null
        }

    /** A failing proof, or an attestor whose storage calls throw, must not stop translation. */
    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallback
        }
}
