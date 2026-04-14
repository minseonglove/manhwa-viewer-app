package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class CreateFolderUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(parentPath: String, folderName: String): Result<String> =
        repository.createFolder(parentPath, folderName)
}
