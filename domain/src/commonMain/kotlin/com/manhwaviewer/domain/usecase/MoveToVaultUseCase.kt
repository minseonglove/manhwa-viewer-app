package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class MoveToVaultUseCase(
    private val archiveFileRepository: ArchiveFileRepository,
) {
    suspend operator fun invoke(filePaths: List<String>): Result<Unit> {
        for (path in filePaths) {
            val moveResult = archiveFileRepository.moveToVault(path)
            moveResult.onSuccess { newPath ->
                val fileName = path.substringAfterLast('/')
                archiveFileRepository.addToVaultWithMigration(
                    originalPath = path,
                    newPath = newPath,
                    relativePath = fileName,
                )
            }
            moveResult.onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }
}
