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

    override suspend fun getCacheCount(): Int = cache.size
}
