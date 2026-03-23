package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.flow.Flow

class GetRecentArchivesUseCase(
    private val repository: ArchiveFileRepository,
) {
    operator fun invoke(): Flow<List<ArchiveFile>> = repository.getRecentArchives()
}
