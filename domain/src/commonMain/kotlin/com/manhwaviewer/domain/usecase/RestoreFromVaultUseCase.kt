package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository
import com.manhwaviewer.domain.repository.VaultFileRepository

class RestoreFromVaultUseCase(
    private val archiveFileRepository: ArchiveFileRepository,
    private val vaultFileRepository: VaultFileRepository,
) {
    /**
     * @return 성공적으로 복원된 파일 수. 부분 실패 시에도 이미 복원된 파일 수를 반환.
     */
    suspend operator fun invoke(vaultFilePaths: List<String>, vaultRootPath: String): Result<Int> {
        var successCount = 0
        for (vaultPath in vaultFilePaths) {
            val relativePath = vaultPath.removePrefix(vaultRootPath).trimStart('/')
            val vaultFile = vaultFileRepository.getByRelativePath(relativePath)
            if (vaultFile == null) {
                return if (successCount > 0) Result.success(successCount)
                else Result.failure(IllegalStateException("No vault record: $relativePath"))
            }

            val originalDir = vaultFile.originalPath.substringBeforeLast('/')
            archiveFileRepository.ensureDirectoryExists(originalDir)

            val moveResult = archiveFileRepository.moveFile(vaultPath, originalDir)
            moveResult.onSuccess { restoredPath ->
                archiveFileRepository.restoreFromVaultWithMigration(vaultPath, restoredPath)
                vaultFileRepository.deleteByRelativePathPrefix(relativePath)
                successCount++
            }
            moveResult.onFailure {
                return if (successCount > 0) Result.success(successCount)
                else Result.failure(it)
            }
        }
        return Result.success(successCount)
    }
}
