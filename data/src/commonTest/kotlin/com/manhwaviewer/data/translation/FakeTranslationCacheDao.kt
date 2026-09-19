package com.manhwaviewer.data.translation

import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.data.db.TranslationCacheEntity

class FakeTranslationCacheDao : TranslationCacheDao {
    private val cache = mutableListOf<TranslationCacheEntity>()

    override suspend fun findCache(
        sourceText: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationCacheEntity? {
        return cache.find {
            it.sourceText == sourceText &&
                it.sourceLanguage == sourceLanguage &&
                it.targetLanguage == targetLanguage
        }
    }

    override suspend fun insertCache(entity: TranslationCacheEntity) {
        cache.removeAll {
            it.sourceText == entity.sourceText &&
                it.sourceLanguage == entity.sourceLanguage &&
                it.targetLanguage == entity.targetLanguage
        }
        cache.add(entity)
    }

    override suspend fun clearAll() {
        cache.clear()
    }

    override suspend fun findCacheByTexts(
        sourceTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<TranslationCacheEntity> {
        return cache.filter {
            it.sourceText in sourceTexts &&
                it.sourceLanguage == sourceLanguage &&
                it.targetLanguage == targetLanguage
        }
    }

    override suspend fun getCacheCount(): Int = cache.size

    // Room 쿼리와 같은 컬럼 집합을 센다 -- 둘이 어긋나면 fake 가 실제 크기를 가린다.
    override suspend fun getTotalDataSize(): Long = cache.sumOf {
        (
            it.sourceText.length + it.translatedText.length +
                it.sourceLanguage.length + it.targetLanguage.length + it.engine.length
            ).toLong()
    }
}
