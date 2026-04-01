package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveFileDao {
    @Query("SELECT * FROM archive_files WHERE lastReadAt IS NOT NULL ORDER BY lastReadAt DESC")
    fun getRecentArchives(): Flow<List<ArchiveFileEntity>>

    @Query("SELECT COUNT(*) FROM archive_files WHERE lastReadAt IS NOT NULL")
    fun getRecentArchivesCount(): Flow<Int>

@Query("SELECT * FROM archive_files WHERE type = 'FOLDER' AND hasImages = 0")
    fun getVaultFiles(): Flow<List<ArchiveFileEntity>>

    @Query("SELECT * FROM archive_files WHERE id = :id")
    suspend fun getById(id: Long): ArchiveFileEntity?

    @Upsert
    suspend fun upsert(entity: ArchiveFileEntity): Long

    @Query("UPDATE archive_files SET lastReadIndex = :pageIndex, lastReadAt = :timestamp WHERE id = :id")
    suspend fun updateLastReadIndex(id: Long, pageIndex: Int, timestamp: Long)

    @Query("SELECT * FROM archive_files WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): ArchiveFileEntity?

    @Query("DELETE FROM archive_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM archive_files WHERE filePath LIKE :directoryPath || '%'")
    suspend fun getByDirectory(directoryPath: String): List<ArchiveFileEntity>
}
