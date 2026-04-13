package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class RenameFileUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(oldPath: String, newName: String): Result<String> =
        repository.renameFile(oldPath, newName)
}
