package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.repository.ArchiveFileRepository

class GetArchiveByIdUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(id: Long): ArchiveFile? = repository.getArchiveById(id)
}
