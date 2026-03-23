package com.manhwaviewer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "archive_files",
    indices = [
        Index("lastReadAt"),
        Index("hasImages"),
        Index("filePath"),
    ],
)
data class ArchiveFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val type: String, // FOLDER, ZIP, CBZ, CBR
    val totalPages: Int = 0,
    val lastReadIndex: Int = 0,
    val lastReadAt: Long? = null, // epoch millis
    val addedAt: Long, // epoch millis
    val hasImages: Boolean = false,
)
