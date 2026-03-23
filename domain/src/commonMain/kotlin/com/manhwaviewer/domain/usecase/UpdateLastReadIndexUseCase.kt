package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class UpdateLastReadIndexUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(archiveId: Long, pageIndex: Int) {
        repository.updateLastReadIndex(archiveId, pageIndex)
    }
}
