package com.manhwaviewer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_translation_cache",
    indices = [
        Index(value = ["archiveId", "page"], unique = true),
    ],
)
data class PageTranslationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val archiveId: Long,
    val page: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocksJson: String,
    val createdAt: Long,
)
