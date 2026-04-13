package com.manhwaviewer.data.cache

import com.manhwaviewer.data.db.PageOcrCacheDao
import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.domain.repository.CacheRepository
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

class IosCacheRepository(
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
        val cacheDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true,
        ).firstOrNull() as? String ?: return 0L
        val coilCachePath = "$cacheDir/coil_disk_cache"
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(coilCachePath)) return 0L

        val contents = fileManager.contentsOfDirectoryAtPath(coilCachePath, null) ?: return 0L
        var totalSize = 0L
        for (i in 0 until contents.count.toInt()) {
            val fileName = contents.objectAtIndex(i.toULong()) as String
            val filePath = "$coilCachePath/$fileName"
            val attrs = fileManager.attributesOfItemAtPath(filePath, null) ?: continue
            val fileSize = attrs["NSFileSize"] as? Long ?: 0L
            totalSize += fileSize
        }
        return totalSize
    }

    override suspend fun clearTranslationCache() {
        translationCacheDao.clearAll()
        pageOcrCacheDao.clearAll()
    }

    override suspend fun clearImageCache() {
        val cacheDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true,
        ).firstOrNull() as? String ?: return
        val coilCachePath = "$cacheDir/coil_disk_cache"
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(coilCachePath)) {
            fileManager.removeItemAtPath(coilCachePath, null)
        }
    }
}
