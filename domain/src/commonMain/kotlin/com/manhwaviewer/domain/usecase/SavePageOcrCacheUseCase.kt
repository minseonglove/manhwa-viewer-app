package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.PageOcrCache
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository

class SavePageOcrCacheUseCase(
    private val repository: PageTranslationCacheRepository,
) {
    suspend operator fun invoke(cache: PageOcrCache): Long {
        return repository.saveOcrCache(cache)
    }
}
