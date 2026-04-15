package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository
import com.manhwaviewer.domain.repository.VaultFileRepository

class DeleteVaultFilesUseCase(
    private val archiveFileRepository: ArchiveFileRepository,
    private val vaultFileRepository: VaultFileRepository,
) {
    /**
     * @return 성공적으로 삭제된 파일 수. 부분 실패 시에도 이미 삭제된 파일 수를 반환.
     */
    suspend operator fun invoke(filePaths: List<String>, vaultRootPath: String): Result<Int> {
        var successCount = 0
        for (path in filePaths) {
            val deleteResult = archiveFileRepository.deleteFile(path)
            deleteResult.onSuccess {
                val relativePath = path.removePrefix(vaultRootPath).trimStart('/')
                vaultFileRepository.deleteByRelativePathPrefix(relativePath)
                successCount++
            }
            deleteResult.onFailure {
                return if (successCount > 0) Result.success(successCount)
                else Result.failure(it)
            }
        }
        return Result.success(successCount)
    }
}
