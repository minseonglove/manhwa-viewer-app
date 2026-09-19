package com.manhwaviewer.data.translation

import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.data.db.TranslationCacheEntity
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.repository.TranslationRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class TranslationRepositoryImpl(
    private val translationService: TranslationService,
    private val cacheDao: TranslationCacheDao,
) : TranslationRepository {

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val result = translateBatch(listOf(text), sourceLanguage, targetLanguage)
        result.first().translatedText
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        // 1. 비어있지 않은 텍스트만 모아서 배치 캐시 조회
        val results = arrayOfNulls<TranslationResult>(texts.size)
        val uncachedIndices = mutableListOf<Int>()
        val nonBlankTexts = texts.withIndex()
            .filter { it.value.isNotBlank() }
            .map { it.value }

        val cacheMap = if (nonBlankTexts.isNotEmpty()) {
            cacheDao.findCacheByTexts(nonBlankTexts, sourceLanguage, targetLanguage)
                .associate { it.sourceText to it.translatedText }
        } else {
            emptyMap()
        }

        for ((index, text) in texts.withIndex()) {
            if (text.isBlank()) {
                results[index] = TranslationResult(text, text)
                continue
            }
            val cached = cacheMap[text]
            if (cached != null) {
                results[index] = TranslationResult(text, cached)
            } else {
                uncachedIndices.add(index)
            }
        }

        // 2. 모두 캐시 히트 시 즉시 반환
        if (uncachedIndices.isEmpty()) {
            return@withContext results.map { it!! }
        }

        // 3. 미캐시 텍스트를 1회 호출로 번역
        val uncachedTexts = uncachedIndices.map { texts[it] }

        val translatedTexts = try {
            translationService.translate(uncachedTexts, sourceLanguage, targetLanguage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[TranslationRepo] Translation API failed: ${e.message}")
            // 번역 실패 시 원문 폴백
            for (idx in uncachedIndices) {
                results[idx] = TranslationResult(texts[idx], texts[idx])
            }
            return@withContext results.map { it!! }
        }

        // 4. 개별 캐시 저장
        val now = Clock.System.now().toEpochMilliseconds()

        for ((i, idx) in uncachedIndices.withIndex()) {
            val original = texts[idx]
            val translated = translatedTexts[i]
            results[idx] = TranslationResult(original, translated)

            cacheDao.insertCache(
                TranslationCacheEntity(
                    sourceText = original,
                    translatedText = translated,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    engine = translationService.engineName,
                    createdAt = now,
                )
            )
        }

        results.map { it!! }
    }
}
