package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.CacheRepository

class GetCacheSizeUseCase(
    private val repository: CacheRepository,
) {
    suspend fun translationCache(): Long = repository.getTranslationCacheSize()
    suspend fun imageCache(): Long = repository.getImageCacheSize()
}
