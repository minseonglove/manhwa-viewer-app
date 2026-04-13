package com.manhwaviewer.data.cache

import android.content.Context
import com.manhwaviewer.data.db.PageOcrCacheDao
import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.domain.repository.CacheRepository
import java.io.File

class AndroidCacheRepository(
    private val context: Context,
    private val translationCacheDao: TranslationCacheDao,
    private val pageOcrCacheDao: PageOcrCacheDao,
) : CacheRepository {

    override suspend fun getTranslationCacheSize(): Long {
        val textCacheSize = translationCacheDao.getTotalDataSize()
        val ocrCacheSize = pageOcrCacheDao.getOcrDataSize()
        val ocrTranslationSize = pageOcrCacheDao.getTranslationDataSize()
        return textCacheSize + ocrCacheSize + ocrTranslationSize
    }

    override suspend fun getImageCacheSize(): Long {
        val coilCache = File(context.cacheDir, "coil_disk_cache")
        if (!coilCache.exists()) return 0L
        return coilCache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun clearTranslationCache() {
        translationCacheDao.clearAll()
        pageOcrCacheDao.clearAll()
    }

    override suspend fun clearImageCache() {
        val coilCache = File(context.cacheDir, "coil_disk_cache")
        if (coilCache.exists()) {
            coilCache.deleteRecursively()
        }
    }
}
