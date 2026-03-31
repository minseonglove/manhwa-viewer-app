package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.CacheRepository

class ClearCacheUseCase(
    private val repository: CacheRepository,
) {
    suspend fun translationCache() = repository.clearTranslationCache()
    suspend fun imageCache() = repository.clearImageCache()
}
