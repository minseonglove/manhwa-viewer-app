package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.PageOcrCache
import com.manhwaviewer.domain.repository.PageTranslationCacheRepository

class GetPageOcrCacheUseCase(
    private val repository: PageTranslationCacheRepository,
) {
    suspend operator fun invoke(archiveId: Long, page: Int): PageOcrCache? {
        return repository.getOcrCache(archiveId, page)
    }
}
