package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.repository.ArchiveFileRepository

class ScanArchiveEntriesUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(archivePath: String): List<ArchiveFile> =
        repository.scanArchiveEntries(archivePath)
}
