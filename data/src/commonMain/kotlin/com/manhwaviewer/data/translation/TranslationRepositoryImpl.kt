package com.manhwaviewer.data.translation

import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.data.db.TranslationCacheEntity
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.repository.TranslationRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class TranslationRepositoryImpl(
    private val papagoService: PapagoTranslationService,
    private val googleService: GoogleTranslationService,
    private val cacheDao: TranslationCacheDao,
) : TranslationRepository {

    companion object {
        private const val BATCH_DELIMITER = "\n"
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        // 1. 캐시 확인
        val cached = cacheDao.findCache(text, sourceLanguage, targetLanguage)
        if (cached != null) return@withContext cached.translatedText

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

        translatedText
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<TranslationResult> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()

        // 1. 각 텍스트별 캐시 조회 → cached / uncached 분리
        val results = arrayOfNulls<TranslationResult>(texts.size)
        val uncachedIndices = mutableListOf<Int>()

        for ((index, text) in texts.withIndex()) {
            if (text.isBlank()) {
                results[index] = TranslationResult(text, text)
                continue
            }
            val cached = cacheDao.findCache(text, sourceLanguage, targetLanguage)
            if (cached != null) {
                results[index] = TranslationResult(text, cached.translatedText)
            } else {
                uncachedIndices.add(index)
            }
        }

        // 2. 모두 캐시 히트 시 즉시 반환
        if (uncachedIndices.isEmpty()) {
            return@withContext results.map { it!! }
        }

        // 3. 미캐시 텍스트를 합쳐서 1회 API 호출
        val uncachedTexts = uncachedIndices.map { texts[it] }
        val joinedText = uncachedTexts.joinToString(BATCH_DELIMITER)

        val translatedJoined = try {
            val googleResult = try {
                googleService.translate(joinedText, sourceLanguage, targetLanguage)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

            googleResult ?: papagoService.translate(joinedText, sourceLanguage, targetLanguage)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 번역 실패 시 원문 폴백
            for (idx in uncachedIndices) {
                results[idx] = TranslationResult(texts[idx], texts[idx])
            }
            return@withContext results.map { it!! }
        }

        // 4. 결과를 구분자로 split → 개별 캐시 저장
        val translatedParts = translatedJoined.split(BATCH_DELIMITER)
        val engine = "PAPAGO"
        val now = Clock.System.now().toEpochMilliseconds()

        for ((i, idx) in uncachedIndices.withIndex()) {
            val original = texts[idx]
            val translated = translatedParts.getOrElse(i) { original }
            results[idx] = TranslationResult(original, translated)

            cacheDao.insertCache(
                TranslationCacheEntity(
                    sourceText = original,
                    translatedText = translated,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    engine = engine,
                    createdAt = now,
                )
            )
        }

        results.map { it!! }
    }
}
