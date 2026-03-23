package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.repository.ArchiveFileRepository

class UpsertArchiveUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(archive: ArchiveFile): Long = repository.upsertArchive(archive)
}
