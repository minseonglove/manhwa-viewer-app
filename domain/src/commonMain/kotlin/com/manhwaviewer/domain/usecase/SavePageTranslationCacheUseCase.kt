package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.PageTranslationCache
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository

class SavePageTranslationCacheUseCase(
    private val repository: PageTranslationCacheRepository,
) {
    suspend operator fun invoke(cache: PageTranslationCache) {
        repository.saveCache(cache)
    }
}
