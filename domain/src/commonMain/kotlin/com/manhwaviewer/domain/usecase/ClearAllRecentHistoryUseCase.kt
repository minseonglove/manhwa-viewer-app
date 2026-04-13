package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class ClearAllRecentHistoryUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke() = repository.clearAllRecentHistory()
}
