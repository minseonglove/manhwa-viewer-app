package com.manhwaviewer.presentation.fake

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

open class FakeArchiveFileRepository : ArchiveFileRepository {
    val recentArchivesFlow = MutableStateFlow<List<ArchiveFile>>(emptyList())
    val recentArchivesCountFlow = MutableStateFlow(0)
    var scannedFiles: List<ArchiveFile> = emptyList()
    var nextUpsertId: Long = 1L

    override fun getRecentArchives(): Flow<List<ArchiveFile>> = recentArchivesFlow
    override fun getRecentArchivesCount(): Flow<Int> = recentArchivesCountFlow
    override suspend fun getArchiveById(id: Long): ArchiveFile? = null
    override suspend fun upsertArchive(archive: ArchiveFile): Long = nextUpsertId
    override suspend fun updateLastReadIndex(id: Long, pageIndex: Int) {}
    override suspend fun deleteArchive(id: Long) {}
    override suspend fun scanLocalFiles(directoryPath: String): List<ArchiveFile> = scannedFiles
    override suspend fun scanArchiveEntries(archivePath: String): List<ArchiveFile> = emptyList()
    override suspend fun getImageEntries(filePath: String): List<String> = emptyList()
    override suspend fun getImageEntriesSorted(filePath: String, sortByDate: Boolean, ascending: Boolean): List<String> = emptyList()
    override suspend fun getImageBytes(filePath: String, entryName: String): ByteArray? = null
    override suspend fun deleteByPath(path: String) {}
    override suspend fun clearRecentHistory(id: Long) {}
    override suspend fun clearAllRecentHistory() {}
    override suspend fun renameFile(oldPath: String, newName: String): Result<String> = Result.success("")
    override suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)
    override suspend fun migratePath(oldPath: String, newPath: String) {}
    override suspend fun createFolder(parentPath: String, folderName: String): Result<String> = Result.success("")
    override suspend fun moveFile(sourcePath: String, destinationDir: String): Result<String> = Result.success("")
    override suspend fun moveToVault(path: String): Result<String> = Result.success("")
    override suspend fun addToVaultWithMigration(originalPath: String, newPath: String, relativePath: String) {}
    override suspend fun addFolderToVaultWithMigration(originalPath: String, newPath: String, relativePath: String, subFileOriginalPaths: List<String>) {}
    override suspend fun restoreFromVaultWithMigration(vaultPath: String, originalPath: String) {}
    override suspend fun isDirectory(path: String): Boolean = false
    override suspend fun listFilesRecursively(path: String): List<String> = emptyList()
    override suspend fun ensureDirectoryExists(path: String) {}
}

fun makeArchive(
    id: Long = 0,
    fileName: String = "test.zip",
    filePath: String = "/storage/$fileName",
    type: ArchiveType = ArchiveType.ZIP,
    lastReadAt: Instant? = null,
    addedAt: Instant = Instant.fromEpochMilliseconds(0),
) = ArchiveFile(
    id = id,
    filePath = filePath,
    fileName = fileName,
    type = type,
    lastReadAt = lastReadAt,
    addedAt = addedAt,
)
