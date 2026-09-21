package com.manhwaviewer.data.translation

import kotlin.coroutines.cancellation.CancellationException

/**
 * Tries [primary] on every call and turns to [fallback] only for the call that failed, so a
 * recovered primary is picked up again without any state to reset.
 */
class FallbackTranslationService(
    private val primary: TranslationService,
    private val fallback: TranslationService,
) : TranslationService {

    /** The engine behind the most recent answer: the repository stores it with each cached translation. */
    override var engineName: String = primary.engineName
        private set

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> {
        val translated = try {
            primary.translate(texts, source, target).also { engineName = primary.engineName }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[Translation] ${primary.engineName} failed (${e.message}), using ${fallback.engineName}")
            fallback.translate(texts, source, target).also { engineName = fallback.engineName }
        }
        return translated
    }
}
