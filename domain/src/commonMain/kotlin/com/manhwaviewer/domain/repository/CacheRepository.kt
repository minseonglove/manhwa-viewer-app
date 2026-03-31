package com.manhwaviewer.domain.repository

interface CacheRepository {
    suspend fun getTranslationCacheSize(): Long  // bytes
    suspend fun getImageCacheSize(): Long  // bytes
    suspend fun clearTranslationCache()
    suspend fun clearImageCache()
}
