package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PageOcrCacheDao {
    @Query(
        "SELECT * FROM page_ocr_cache " +
            "WHERE archiveId = :archiveId AND page = :page " +
            "LIMIT 1"
    )
    suspend fun findOcrCache(archiveId: Long, page: Int): PageOcrCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrCache(entity: PageOcrCacheEntity): Long

    @Query("DELETE FROM page_ocr_cache WHERE archiveId = :archiveId")
    suspend fun deleteByArchive(archiveId: Long)

    @Query(
        "SELECT * FROM page_ocr_translation " +
            "WHERE ocrCacheId = :ocrCacheId " +
            "LIMIT 1"
    )
    suspend fun findTranslation(ocrCacheId: Long): PageOcrTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(entity: PageOcrTranslationEntity)
}
