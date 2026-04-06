package com.manhwaviewer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_ocr_translation",
    foreignKeys = [
        ForeignKey(
            entity = PageOcrCacheEntity::class,
            parentColumns = ["id"],
            childColumns = ["ocrCacheId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["ocrCacheId"], unique = true),
    ],
)
data class PageOcrTranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ocrCacheId: Long,
    val translationsJson: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val createdAt: Long,
)
