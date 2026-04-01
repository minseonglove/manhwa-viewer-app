package com.manhwaviewer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["sourceText", "sourceLanguage", "targetLanguage"], unique = true),
    ],
)
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val engine: String, // "PAPAGO", "GOOGLE_CLOUD" 등
    val createdAt: Long, // epoch millis
)
