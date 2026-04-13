package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class ClearRecentHistoryUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(id: Long) = repository.clearRecentHistory(id)
}
