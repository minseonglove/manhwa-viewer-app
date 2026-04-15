package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class MoveFilesUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(filePaths: List<String>, destinationDir: String): Result<Unit> {
        val movedPairs = mutableListOf<Pair<String, String>>()

        for (path in filePaths) {
            val moveResult = repository.moveFile(path, destinationDir)
            moveResult.onSuccess { newPath ->
                movedPairs.add(path to newPath)
            }
            moveResult.onFailure {
                // 이미 이동된 파일들의 DB 경로는 갱신
                migratePaths(movedPairs)
                return Result.failure(it)
            }
        }

        return try {
            migratePaths(movedPairs)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun migratePaths(pairs: List<Pair<String, String>>) {
        for ((oldPath, newPath) in pairs) {
            try {
                repository.migratePath(oldPath, newPath)
            } catch (e: Exception) {
                // 개별 migratePath 실패는 로깅 후 계속 진행
                // 파일은 이미 이동되었으므로 나머지 경로도 갱신 시도
                e.printStackTrace()
            }
        }
    }
}
