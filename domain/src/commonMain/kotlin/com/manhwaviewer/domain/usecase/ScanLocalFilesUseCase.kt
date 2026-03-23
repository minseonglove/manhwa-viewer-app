package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.repository.ArchiveFileRepository

class ScanLocalFilesUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(directoryPath: String): List<ArchiveFile> =
        repository.scanLocalFiles(directoryPath)
}
