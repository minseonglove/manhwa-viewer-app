package com.manhwaviewer.domain.entity

import kotlin.time.Instant

data class ImageFile(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val dateModified: Instant,
    val folderName: String,
)
