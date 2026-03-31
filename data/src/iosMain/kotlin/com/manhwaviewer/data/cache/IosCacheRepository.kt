package com.manhwaviewer.data.cache

import com.manhwaviewer.domain.repository.CacheRepository

class IosCacheRepository : CacheRepository {

    override suspend fun getTranslationCacheSize(): Long = 0L

    override suspend fun getImageCacheSize(): Long = 0L

    override suspend fun clearTranslationCache() {
        // iOS implementation TBD
    }

    override suspend fun clearImageCache() {
        // iOS implementation TBD
    }
}
