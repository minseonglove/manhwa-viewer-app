package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.PageTranslationCache
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository

class GetPageTranslationCacheUseCase(
    private val repository: PageTranslationCacheRepository,
) {
    suspend operator fun invoke(archiveId: Long, entryName: String): PageTranslationCache? {
        return repository.getCache(archiveId, entryName)
    }
}
