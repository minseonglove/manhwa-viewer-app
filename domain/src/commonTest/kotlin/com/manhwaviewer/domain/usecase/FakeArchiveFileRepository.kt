package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

class FakeArchiveFileRepository : ArchiveFileRepository {
    val recentArchives = MutableStateFlow<List<ArchiveFile>>(emptyList())
    val recentCount = MutableStateFlow(0)
    var scanResult: List<ArchiveFile> = emptyList()

    override fun getRecentArchives(): Flow<List<ArchiveFile>> = recentArchives
    override fun getRecentArchivesCount(): Flow<Int> = recentCount
    override suspend fun getArchiveById(id: Long): ArchiveFile? = null
    override suspend fun upsertArchive(archive: ArchiveFile): Long = archive.id
    override suspend fun updateLastReadIndex(id: Long, pageIndex: Int) {}
    override suspend fun deleteArchive(id: Long) {}
    override suspend fun clearRecentHistory(id: Long) {}
    override suspend fun clearAllRecentHistory() {}
    override suspend fun scanLocalFiles(directoryPath: String): List<ArchiveFile> = scanResult
    override suspend fun scanArchiveEntries(archivePath: String): List<ArchiveFile> = emptyList()
    override suspend fun getImageEntries(filePath: String): List<String> = emptyList()
    override suspend fun getImageEntriesSorted(filePath: String, sortByDate: Boolean, ascending: Boolean): List<String> = emptyList()
    override suspend fun getImageBytes(filePath: String, entryName: String): ByteArray? = null
    override suspend fun deleteByPath(path: String) {}
    override suspend fun renameFile(oldPath: String, newName: String): Result<String> =
        Result.success(oldPath.substringBeforeLast('/', "") + "/" + newName)
    override suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)
    override suspend fun migratePath(oldPath: String, newPath: String) {}
    override suspend fun createFolder(parentPath: String, folderName: String): Result<String> =
        Result.success("$parentPath/$folderName")
    override suspend fun moveFile(sourcePath: String, destinationDir: String): Result<String> =
        Result.success("$destinationDir/" + sourcePath.substringAfterLast('/'))
    override suspend fun moveToVault(path: String): Result<String> = Result.success(path)
    override suspend fun addToVaultWithMigration(originalPath: String, newPath: String, relativePath: String) {}
    override suspend fun addFolderToVaultWithMigration(
        originalPath: String,
        newPath: String,
        relativePath: String,
        subFileOriginalPaths: List<String>,
    ) {}
    override suspend fun restoreFromVaultWithMigration(vaultPath: String, originalPath: String) {}
    override suspend fun isDirectory(path: String): Boolean = false
    override suspend fun listFilesRecursively(path: String): List<String> = emptyList()
    override suspend fun ensureDirectoryExists(path: String) {}

    companion object {
        fun sampleArchive(
            id: Long = 1L,
            fileName: String = "test.zip",
            type: ArchiveType = ArchiveType.ZIP,
            hasImages: Boolean = true,
        ) = ArchiveFile(
            id = id,
            filePath = "/storage/$fileName",
            fileName = fileName,
            type = type,
            totalPages = 100,
            lastReadIndex = 50,
            addedAt = Instant.fromEpochMilliseconds(1000000L),
            hasImages = hasImages,
        )
    }
}
