package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class SearchResult(
    val folders: List<ArchiveFile>,
    val archives: List<ArchiveFile>,
    val images: List<ArchiveFile>,
)

class SearchFilesUseCase(
    private val repository: ArchiveFileRepository,
) {
    private var cachedFiles: List<ArchiveFile>? = null
    private var cachedPath: String? = null

    suspend operator fun invoke(directoryPath: String, query: String): SearchResult {
        if (query.isBlank()) return SearchResult(emptyList(), emptyList(), emptyList())

        val allFiles = if (cachedPath == directoryPath && cachedFiles != null) {
            cachedFiles!!
        } else {
            val scanned = scanRecursively(directoryPath)
            cachedFiles = scanned
            cachedPath = directoryPath
            scanned
        }

        val lowerQuery = query.lowercase()
        val matched = allFiles.filter { it.displayName().lowercase().contains(lowerQuery) }

        return SearchResult(
            folders = matched.filter { it.type == ArchiveType.FOLDER },
            archives = matched.filter { it.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) },
            images = matched.filter { it.type == ArchiveType.IMAGE },
        )
    }

    fun invalidateCache() {
        cachedFiles = null
        cachedPath = null
    }

    private suspend fun scanRecursively(directoryPath: String): List<ArchiveFile> {
        val files = repository.scanLocalFiles(directoryPath)
        val folders = files.filter { it.type == ArchiveType.FOLDER }
        val nonFolders = files.filter { it.type != ArchiveType.FOLDER }

        if (folders.isEmpty()) return nonFolders

        val subResults = coroutineScope {
            folders.map { folder ->
                async { scanRecursively(folder.filePath) }
            }.awaitAll().flatten()
        }

        return files + subResults
    }

    private fun ArchiveFile.displayName(): String = when (type) {
        ArchiveType.FOLDER -> filePath.trimEnd('/').substringAfterLast('/')
        else -> fileName
    }
}
