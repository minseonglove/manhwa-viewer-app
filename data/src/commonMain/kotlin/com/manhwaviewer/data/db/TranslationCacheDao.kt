package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()

    @Query(
        "SELECT * FROM translation_cache WHERE sourceText IN (:sourceTexts) AND sourceLanguage = :sourceLanguage AND targetLanguage = :targetLanguage"
    )
    suspend fun findCacheByTexts(
        sourceTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<TranslationCacheEntity>

    @Query("SELECT COALESCE(SUM(LENGTH(sourceText) + LENGTH(translatedText) + LENGTH(sourceLanguage) + LENGTH(targetLanguage) + LENGTH(engine)), 0) FROM translation_cache")
    suspend fun getTotalDataSize(): Long
}
