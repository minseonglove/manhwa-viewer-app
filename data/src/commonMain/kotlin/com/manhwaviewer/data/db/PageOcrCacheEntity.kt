package com.manhwaviewer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_ocr_cache",
    indices = [
        Index(value = ["archiveId", "entryName"], unique = true),
    ],
)
data class PageOcrCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val archiveId: Long,
    val entryName: String,
    val page: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocksJson: String,
    val createdAt: Long,
)
