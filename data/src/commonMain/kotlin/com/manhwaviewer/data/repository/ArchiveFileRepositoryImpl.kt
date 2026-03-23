package com.manhwaviewer.data.repository

import com.manhwaviewer.data.archive.ArchiveReader
import com.manhwaviewer.data.db.ArchiveFileDao
import com.manhwaviewer.data.db.toDomain
import com.manhwaviewer.data.db.toEntity
import com.manhwaviewer.data.scanner.MediaScanner
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class ArchiveFileRepositoryImpl(
    private val dao: ArchiveFileDao,
    private val mediaScanner: MediaScanner,
    private val archiveReader: ArchiveReader,
) : ArchiveFileRepository {

    override fun getRecentArchives(): Flow<List<ArchiveFile>> =
        dao.getRecentArchives().map { entities -> entities.map { it.toDomain() } }

    override fun getRecentArchivesCount(): Flow<Int> =
        dao.getRecentArchivesCount()

override fun getVaultFiles(): Flow<List<ArchiveFile>> =
        dao.getVaultFiles().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getArchiveById(id: Long): ArchiveFile? =
        dao.getById(id)?.toDomain()

    override suspend fun upsertArchive(archive: ArchiveFile): Long =
        dao.upsert(archive.toEntity())

    override suspend fun updateLastReadIndex(id: Long, pageIndex: Int) {
        dao.updateLastReadIndex(id, pageIndex, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun deleteArchive(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun scanLocalFiles(directoryPath: String): List<ArchiveFile> =
        mediaScanner.scanDirectory(directoryPath)

    override suspend fun scanArchiveEntries(archivePath: String): List<ArchiveFile> {
        val entries = archiveReader.getImageEntries(archivePath)
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
        archiveReader.getImageEntries(filePath)

    override suspend fun getImageBytes(filePath: String, entryName: String): ByteArray? =
        archiveReader.getImageBytes(filePath, entryName)
}
