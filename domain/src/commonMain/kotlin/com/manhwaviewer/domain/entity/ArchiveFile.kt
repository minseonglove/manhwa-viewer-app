package com.manhwaviewer.domain.entity

import kotlin.time.Instant

data class ArchiveFile(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val type: ArchiveType,
    val totalPages: Int = 0,
    val lastReadIndex: Int = 0,
    val lastReadAt: Instant? = null,
    val addedAt: Instant,
    val hasImages: Boolean = false,
)

enum class ArchiveType {
    FOLDER, ZIP, CBZ, CBR, IMAGE
}
