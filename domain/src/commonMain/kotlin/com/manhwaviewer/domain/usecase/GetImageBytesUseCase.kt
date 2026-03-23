package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class GetImageBytesUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(filePath: String, entryName: String): ByteArray? =
        repository.getImageBytes(filePath, entryName)
}
