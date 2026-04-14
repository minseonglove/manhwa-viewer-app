package com.manhwaviewer.data.db

import com.manhwaviewer.domain.entity.VaultFile
import kotlin.time.Instant

fun VaultFileEntity.toDomain(): VaultFile = VaultFile(
    id = id,
    relativePath = relativePath,
    originalPath = originalPath,
    addedAt = Instant.fromEpochMilliseconds(addedAt),
)

fun VaultFile.toEntity(): VaultFileEntity = VaultFileEntity(
    id = id,
    relativePath = relativePath,
    originalPath = originalPath,
    addedAt = addedAt.toEpochMilliseconds(),
)
