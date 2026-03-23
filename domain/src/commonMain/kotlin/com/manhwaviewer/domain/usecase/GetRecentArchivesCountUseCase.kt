package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.flow.Flow

class GetRecentArchivesCountUseCase(
    private val repository: ArchiveFileRepository,
) {
    operator fun invoke(): Flow<Int> = repository.getRecentArchivesCount()
}
