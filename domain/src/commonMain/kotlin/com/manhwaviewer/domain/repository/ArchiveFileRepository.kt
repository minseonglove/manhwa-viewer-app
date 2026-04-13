package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.ArchiveFile
import kotlinx.coroutines.flow.Flow

interface ArchiveFileRepository {
    fun getRecentArchives(): Flow<List<ArchiveFile>>
    fun getRecentArchivesCount(): Flow<Int>

    fun getVaultFiles(): Flow<List<ArchiveFile>>
    suspend fun getArchiveById(id: Long): ArchiveFile?
    suspend fun upsertArchive(archive: ArchiveFile): Long
    suspend fun updateLastReadIndex(id: Long, pageIndex: Int)
    suspend fun deleteArchive(id: Long)
    suspend fun clearRecentHistory(id: Long)
    suspend fun clearAllRecentHistory()
    suspend fun scanLocalFiles(directoryPath: String): List<ArchiveFile>
    suspend fun scanArchiveEntries(archivePath: String): List<ArchiveFile>
    suspend fun getImageEntries(filePath: String): List<String>
    suspend fun getImageEntriesSorted(filePath: String, sortByDate: Boolean, ascending: Boolean): List<String>
    suspend fun getImageBytes(filePath: String, entryName: String): ByteArray?
    suspend fun deleteByPath(path: String)
    suspend fun migratePath(oldPath: String, newPath: String)
}
