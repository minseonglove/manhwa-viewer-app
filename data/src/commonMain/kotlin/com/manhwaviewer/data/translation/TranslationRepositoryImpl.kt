package com.manhwaviewer.data.translation

import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.data.db.TranslationCacheEntity
import com.manhwaviewer.domain.repository.TranslationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.Clock

class TranslationRepositoryImpl(
    private val papagoService: PapagoTranslationService,
    private val googleService: GoogleTranslationService,
    private val cacheDao: TranslationCacheDao,
) : TranslationRepository {

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        if (text.isBlank()) return text

        // 1. 캐시 확인
        val cached = cacheDao.findCache(text, sourceLanguage, targetLanguage)
        if (cached != null) return cached.translatedText

        // 2. Google 번역 시도 (현재 TODO - null 반환)
        val googleResult = try {
            googleService.translate(text, sourceLanguage, targetLanguage)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        // 3. Google 성공 시 반환, 실패 시 Papago로 폴백
        val translatedText = googleResult ?: try {
            papagoService.translate(text, sourceLanguage, targetLanguage)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            text // 원문 폴백
        }

        // 4. 캐시 저장
        cacheDao.insertCache(
            TranslationCacheEntity(
                sourceText = text,
                translatedText = translatedText,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                engine = if (googleResult != null) "GOOGLE_CLOUD" else "PAPAGO",
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )

        return translatedText
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String> = coroutineScope {
        texts.map { text ->
            async { translate(text, sourceLanguage, targetLanguage) }
        }.awaitAll()
    }
}
