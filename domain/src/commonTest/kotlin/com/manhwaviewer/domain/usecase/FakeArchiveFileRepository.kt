package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.repository.ArchiveFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class FakeArchiveFileRepository : ArchiveFileRepository {
    val recentArchives = MutableStateFlow<List<ArchiveFile>>(emptyList())
    val recentCount = MutableStateFlow(0)
    val vaultFiles = MutableStateFlow<List<ArchiveFile>>(emptyList())
    var scanResult: List<ArchiveFile> = emptyList()

    override fun getRecentArchives(): Flow<List<ArchiveFile>> = recentArchives
    override fun getRecentArchivesCount(): Flow<Int> = recentCount
    override fun getVaultFiles(): Flow<List<ArchiveFile>> = vaultFiles
    override suspend fun getArchiveById(id: Long): ArchiveFile? = null
    override suspend fun upsertArchive(archive: ArchiveFile): Long = archive.id
    override suspend fun updateLastReadIndex(id: Long, pageIndex: Int) {}
    override suspend fun deleteArchive(id: Long) {}
    override suspend fun scanLocalFiles(directoryPath: String): List<ArchiveFile> = scanResult
    override suspend fun scanArchiveEntries(archivePath: String): List<ArchiveFile> = emptyList()
    override suspend fun getImageEntries(filePath: String): List<String> = emptyList()
    override suspend fun getImageEntriesSorted(filePath: String, sortByDate: Boolean, ascending: Boolean): List<String> = emptyList()
    override suspend fun getImageBytes(filePath: String, entryName: String): ByteArray? = null

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
