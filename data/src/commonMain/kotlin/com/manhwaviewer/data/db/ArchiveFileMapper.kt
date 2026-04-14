package com.manhwaviewer.data.db

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlin.time.Instant

fun ArchiveFileEntity.toDomain(): ArchiveFile = ArchiveFile(
    id = id,
    filePath = filePath,
    fileName = fileName,
    type = when (type) {
        ArchiveType.FOLDER.name -> ArchiveType.FOLDER
        ArchiveType.ZIP.name -> ArchiveType.ZIP
        ArchiveType.CBZ.name -> ArchiveType.CBZ
        ArchiveType.CBR.name -> ArchiveType.CBR
        else -> ArchiveType.FOLDER
    },
    totalPages = totalPages,
    lastReadIndex = lastReadIndex,
    lastReadAt = lastReadAt?.let { Instant.fromEpochMilliseconds(it) },
    addedAt = Instant.fromEpochMilliseconds(addedAt),
    hasImages = hasImages,
)

fun ArchiveFile.toEntity(): ArchiveFileEntity = ArchiveFileEntity(
    id = id,
    filePath = filePath,
    fileName = fileName,
    type = type.name,
    totalPages = totalPages,
    lastReadIndex = lastReadIndex,
    lastReadAt = lastReadAt?.toEpochMilliseconds(),
    addedAt = addedAt.toEpochMilliseconds(),
    hasImages = hasImages,
)
