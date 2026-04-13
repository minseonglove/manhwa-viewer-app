package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.PageOcrCache
import com.manhwaviewer.domain.entity.PageTranslationCache

interface PageTranslationCacheRepository {
    suspend fun getCache(archiveId: Long, entryName: String): PageTranslationCache?
    suspend fun saveCache(cache: PageTranslationCache)
    suspend fun deleteByArchive(archiveId: Long)

    suspend fun getOcrCache(archiveId: Long, entryName: String): PageOcrCache?
    suspend fun saveOcrCache(cache: PageOcrCache): Long
}
