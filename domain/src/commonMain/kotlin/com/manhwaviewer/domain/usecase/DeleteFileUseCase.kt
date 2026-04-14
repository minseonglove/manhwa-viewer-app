package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class DeleteFileUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(path: String): Result<Unit> =
        repository.deleteFile(path)
}
