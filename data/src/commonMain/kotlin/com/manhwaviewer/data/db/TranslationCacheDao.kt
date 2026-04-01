package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {
    @Query(
        "SELECT * FROM translation_cache WHERE sourceText = :sourceText AND sourceLanguage = :sourceLanguage AND targetLanguage = :targetLanguage LIMIT 1"
    )
    suspend fun findCache(sourceText: String, sourceLanguage: String, targetLanguage: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun getCacheCount(): Int
}
