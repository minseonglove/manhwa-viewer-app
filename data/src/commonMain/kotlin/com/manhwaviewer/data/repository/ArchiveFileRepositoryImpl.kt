package com.manhwaviewer.data.repository

import com.manhwaviewer.data.archive.ArchiveReader
import com.manhwaviewer.data.db.ArchiveFileDao
import com.manhwaviewer.data.db.PageOcrCacheDao
import com.manhwaviewer.data.db.toDomain
import com.manhwaviewer.data.db.toEntity
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ArchiveFileRepositoryImpl(
    private val dao: ArchiveFileDao,
    private val mediaScanner: MediaScanner,
    private val archiveReader: ArchiveReader,
    private val pageOcrCacheDao: PageOcrCacheDao,
) : ArchiveFileRepository {

    override fun getRecentArchives(): Flow<List<ArchiveFile>> =
        dao.getRecentArchives().map { entities -> entities.map { it.toDomain() } }

    override fun getRecentArchivesCount(): Flow<Int> =
        dao.getRecentArchivesCount()

    override suspend fun getArchiveById(id: Long): ArchiveFile? =
        dao.getById(id)?.toDomain()

    override suspend fun upsertArchive(archive: ArchiveFile): Long {
        val existing = dao.getByFilePath(archive.filePath)
        val entity = if (existing != null) {
            archive.toEntity().copy(
                id = existing.id,
                addedAt = existing.addedAt,
                lastReadIndex = existing.lastReadIndex,
                lastReadAt = existing.lastReadAt,
            )
        } else {
            archive.toEntity()
        }
        val rowId = dao.upsert(entity)
        // Room @Upsert returns -1 on update, so use existing id in that case
        return if (rowId == -1L) existing!!.id else rowId
    }

    override suspend fun updateLastReadIndex(id: Long, pageIndex: Int) {
        dao.updateLastReadIndex(id, pageIndex, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun deleteArchive(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearRecentHistory(id: Long) {
        dao.clearRecentHistory(id)
    }

    override suspend fun clearAllRecentHistory() {
        dao.clearAllRecentHistory()
    }

    override suspend fun scanLocalFiles(directoryPath: String): List<ArchiveFile> =
        withContext(Dispatchers.IO) {
            mediaScanner.scanDirectory(directoryPath)
        }

    override suspend fun scanArchiveEntries(archivePath: String): List<ArchiveFile> {
        val entries = withContext(Dispatchers.IO) { archiveReader.getImageEntries(archivePath) }
        val ext = archivePath.substringAfterLast('.').lowercase()
        val archiveType = when (ext) {
            "cbz" -> ArchiveType.CBZ
            "cbr" -> ArchiveType.CBR
            else -> ArchiveType.ZIP
        }
        val now = Clock.System.now()
        return entries.mapIndexed { index, entryName ->
            ArchiveFile(
                filePath = archivePath,
                fileName = entryName,
                type = archiveType,
                totalPages = entries.size,
                lastReadIndex = index,
                addedAt = now,
                hasImages = true,
            )
        }
    }

    override suspend fun getImageEntries(filePath: String): List<String> =
        withContext(Dispatchers.IO) {
            archiveReader.getImageEntries(filePath)
        }

    override suspend fun getImageEntriesSorted(filePath: String, sortByDate: Boolean, ascending: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            archiveReader.getImageEntriesSorted(filePath, sortByDate, ascending)
        }

    override suspend fun getImageBytes(filePath: String, entryName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            archiveReader.getImageBytes(filePath, entryName)
        }

    override suspend fun deleteByPath(path: String) {
        dao.deleteByPathWithOcrCache(path)
    }

    override suspend fun deleteFile(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val result = mediaScanner.deleteFile(path)
            result.onSuccess {
                deleteByPath(path)
            }
            result
        }

    override suspend fun renameFile(oldPath: String, newName: String): Result<String> =
        withContext(Dispatchers.IO) {
            val result = mediaScanner.renameFile(oldPath, newName)
            result.onSuccess { newPath ->
                migratePath(oldPath, newPath)
            }
            result
        }

    override suspend fun createFolder(parentPath: String, folderName: String): Result<String> =
        withContext(Dispatchers.IO) {
            mediaScanner.createFolder(parentPath, folderName)
        }

    override suspend fun migratePath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        val single = dao.getByFilePath(oldPath)
        if (single != null) {
            dao.updateFilePath(oldPath, newPath)
        } else {
            dao.updateDirectoryPath(oldPath, newPath)
        }
    }

    override suspend fun moveToVault(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            mediaScanner.moveFile(path, mediaScanner.getVaultPath())
        }

    override suspend fun addToVaultWithMigration(originalPath: String, newPath: String, relativePath: String) =
        withContext(Dispatchers.IO) {
            dao.addToVaultWithMigration(
                originalPath = originalPath,
                newPath = newPath,
                relativePath = relativePath,
                addedAt = Clock.System.now().toEpochMilliseconds(),
            )
        }
}
