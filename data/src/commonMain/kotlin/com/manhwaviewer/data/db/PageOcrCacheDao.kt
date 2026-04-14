package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PageOcrCacheDao {
    @Query(
        "SELECT * FROM page_ocr_cache " +
            "WHERE archiveId = :archiveId AND entryName = :entryName " +
            "LIMIT 1"
    )
    abstract suspend fun findOcrCache(archiveId: Long, entryName: String): PageOcrCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOcrCache(entity: PageOcrCacheEntity): Long

    @Query("DELETE FROM page_ocr_cache WHERE archiveId = :archiveId")
    abstract suspend fun deleteByArchive(archiveId: Long)

    @Query(
        "SELECT * FROM page_ocr_translation " +
            "WHERE ocrCacheId = :ocrCacheId " +
            "LIMIT 1"
    )
    abstract suspend fun findTranslation(ocrCacheId: Long): PageOcrTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTranslation(entity: PageOcrTranslationEntity)

    @Query("SELECT COALESCE(SUM(LENGTH(blocksJson)), 0) FROM page_ocr_cache")
    abstract suspend fun getOcrDataSize(): Long

    @Query("SELECT COALESCE(SUM(LENGTH(translationsJson)), 0) FROM page_ocr_translation")
    abstract suspend fun getTranslationDataSize(): Long

    @Query("DELETE FROM page_ocr_cache")
    abstract suspend fun clearAll()

    @Query("DELETE FROM page_ocr_cache WHERE archiveId IN (SELECT id FROM archive_files WHERE filePath = :filePath)")
    abstract suspend fun deleteOcrCacheByFilePath(filePath: String)

    @Query("DELETE FROM page_ocr_cache WHERE archiveId IN (SELECT id FROM archive_files WHERE filePath LIKE :directoryPath || '/%')")
    abstract suspend fun deleteOcrCacheByDirectory(directoryPath: String)

    @Transaction
    open suspend fun insertOcrCacheWithTranslation(
        ocrEntity: PageOcrCacheEntity,
        translationEntity: PageOcrTranslationEntity,
    ) {
        val ocrCacheId = insertOcrCache(ocrEntity)
        insertTranslation(translationEntity.copy(ocrCacheId = ocrCacheId))
    }
}
