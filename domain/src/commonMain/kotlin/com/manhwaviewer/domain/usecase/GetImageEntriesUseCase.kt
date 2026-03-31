package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.ArchiveFileRepository

class GetImageEntriesUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(filePath: String): List<String> =
        repository.getImageEntries(filePath)

    suspend operator fun invoke(filePath: String, sortByDate: Boolean, ascending: Boolean): List<String> =
        repository.getImageEntriesSorted(filePath, sortByDate, ascending)
}
