package com.manhwaviewer.data.cache

import android.content.Context
import com.manhwaviewer.domain.repository.CacheRepository
import java.io.File

class AndroidCacheRepository(
    private val context: Context,
) : CacheRepository {

    override suspend fun getTranslationCacheSize(): Long {
        val dbFile = context.getDatabasePath("manhwa_viewer.db")
        return if (dbFile.exists()) dbFile.length() else 0L
    }

    override suspend fun getImageCacheSize(): Long {
        val cacheDir = File(context.cacheDir, "image_cache")
        val coilCache = File(context.cacheDir, "image_loader_cache")
        return listOf(cacheDir, coilCache)
            .filter { it.exists() }
            .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    override suspend fun clearTranslationCache() {
        // Translation cache is stored in Room DB - for now just a placeholder
        // Full implementation will clear specific translation tables
    }

    override suspend fun clearImageCache() {
        val cacheDir = File(context.cacheDir, "image_cache")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
        // Also clear Coil's disk cache
        val coilCache = File(context.cacheDir, "image_loader_cache")
        if (coilCache.exists()) {
            coilCache.deleteRecursively()
        }
    }
}
