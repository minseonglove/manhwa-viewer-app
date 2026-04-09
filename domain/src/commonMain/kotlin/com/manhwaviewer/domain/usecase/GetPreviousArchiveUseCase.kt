package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import com.manhwaviewer.domain.util.naturalSortKey

class GetPreviousArchiveUseCase(
    private val repository: ArchiveFileRepository,
) {
    suspend operator fun invoke(currentFilePath: String): ArchiveFile? {
        val parentDir = currentFilePath.substringBeforeLast('/')
        if (parentDir == currentFilePath) return null

        val siblings = repository.scanLocalFiles(parentDir)
            .filter { it.type != ArchiveType.IMAGE && it.hasImages }

        // 같은 basename의 폴더+압축파일이 공존하면 폴더 우선, 압축파일 제외
        val folderBaseNames = siblings
            .filter { it.type == ArchiveType.FOLDER }
            .map { it.fileName }
            .toSet()

        val filtered = siblings.filter { file ->
            if (file.type != ArchiveType.FOLDER) {
                val baseName = file.fileName.substringBeforeLast('.')
                baseName !in folderBaseNames
            } else {
                true
            }
        }

        val sorted = filtered.sortedBy { naturalSortKey(it.fileName) }
        val currentIndex = sorted.indexOfFirst { it.filePath == currentFilePath }
        if (currentIndex <= 0) return null

        return sorted[currentIndex - 1]
    }
}
