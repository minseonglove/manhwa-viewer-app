package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PageTranslationCacheDao {
    @Query(
        "SELECT * FROM page_translation_cache " +
            "WHERE archiveId = :archiveId AND page = :page " +
            "LIMIT 1"
    )
    suspend fun findCache(archiveId: Long, page: Int): PageTranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: PageTranslationCacheEntity)

    @Query("DELETE FROM page_translation_cache WHERE archiveId = :archiveId")
    suspend fun deleteByArchive(archiveId: Long)
}
