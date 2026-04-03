package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.PageTranslationCache

interface PageTranslationCacheRepository {
    suspend fun getCache(archiveId: Long, page: Int): PageTranslationCache?
    suspend fun saveCache(cache: PageTranslationCache)
    suspend fun deleteByArchive(archiveId: Long)
}
