package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class MoveToVaultUseCase(
    private val archiveFileRepository: ArchiveFileRepository,
) {
    suspend operator fun invoke(filePaths: List<String>): Result<Unit> {
        for (path in filePaths) {
            val fileName = path.substringAfterLast('/')
            val isDir = archiveFileRepository.isDirectory(path)
            val subFiles = if (isDir) archiveFileRepository.listFilesRecursively(path) else emptyList()

            val moveResult = archiveFileRepository.moveToVault(path)
            moveResult.onSuccess { newPath ->
                if (isDir && subFiles.isNotEmpty()) {
                    archiveFileRepository.addFolderToVaultWithMigration(
                        originalPath = path,
                        newPath = newPath,
                        relativePath = fileName,
                        subFileOriginalPaths = subFiles,
                    )
                } else {
                    archiveFileRepository.addToVaultWithMigration(
                        originalPath = path,
                        newPath = newPath,
                        relativePath = fileName,
                    )
                }
            }
            moveResult.onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }
}
