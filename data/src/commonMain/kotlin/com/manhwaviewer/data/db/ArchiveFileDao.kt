package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ArchiveFileDao {
    @Query("SELECT * FROM archive_files WHERE lastReadAt IS NOT NULL ORDER BY lastReadAt DESC")
    abstract fun getRecentArchives(): Flow<List<ArchiveFileEntity>>

    @Query("SELECT COUNT(*) FROM archive_files WHERE lastReadAt IS NOT NULL")
    abstract fun getRecentArchivesCount(): Flow<Int>

    @Query("SELECT * FROM archive_files WHERE id = :id")
    abstract suspend fun getById(id: Long): ArchiveFileEntity?

    @Upsert
    abstract suspend fun upsert(entity: ArchiveFileEntity): Long

    @Query("UPDATE archive_files SET lastReadIndex = :pageIndex, lastReadAt = :timestamp WHERE id = :id")
    abstract suspend fun updateLastReadIndex(id: Long, pageIndex: Int, timestamp: Long)

    @Query("SELECT * FROM archive_files WHERE filePath = :filePath LIMIT 1")
    abstract suspend fun getByFilePath(filePath: String): ArchiveFileEntity?

    @Query("DELETE FROM archive_files WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("UPDATE archive_files SET lastReadAt = NULL, lastReadIndex = 0 WHERE id = :id")
    abstract suspend fun clearRecentHistory(id: Long)

    @Query("UPDATE archive_files SET lastReadAt = NULL, lastReadIndex = 0 WHERE lastReadAt IS NOT NULL")
    abstract suspend fun clearAllRecentHistory()

    @Query("DELETE FROM archive_files WHERE filePath = :filePath")
    abstract suspend fun deleteByFilePath(filePath: String)

    @Query("DELETE FROM archive_files WHERE filePath LIKE :directoryPath || '/%'")
    abstract suspend fun deleteByDirectory(directoryPath: String)

    @Query("UPDATE archive_files SET filePath = :newPath WHERE filePath = :oldPath")
    abstract suspend fun updateFilePath(oldPath: String, newPath: String)

    @Query("UPDATE archive_files SET filePath = :newDirectory || SUBSTR(filePath, LENGTH(:oldDirectory) + 1) WHERE filePath LIKE :oldDirectory || '/%'")
    abstract suspend fun updateDirectoryPath(oldDirectory: String, newDirectory: String)

    // ── cross-concern 쿼리 (트랜잭션용) ──────────────────────────

    @Query("DELETE FROM page_ocr_cache WHERE archiveId IN (SELECT id FROM archive_files WHERE filePath = :filePath)")
    abstract suspend fun deleteOcrCacheByFilePath(filePath: String)

    @Query("DELETE FROM page_ocr_cache WHERE archiveId IN (SELECT id FROM archive_files WHERE filePath LIKE :directoryPath || '/%')")
    abstract suspend fun deleteOcrCacheByDirectory(directoryPath: String)

    @Query("INSERT OR REPLACE INTO vault_files (relativePath, originalPath, addedAt) VALUES (:relativePath, :originalPath, :addedAt)")
    abstract suspend fun insertVaultFile(relativePath: String, originalPath: String, addedAt: Long)

    // ── @Transaction 메서드 ──────────────────────────────────────

    @Transaction
    open suspend fun deleteByPathWithOcrCache(path: String) {
        val single = getByFilePath(path)
        if (single != null) {
            deleteOcrCacheByFilePath(path)
            deleteByFilePath(path)
        } else {
            deleteOcrCacheByDirectory(path)
            deleteByDirectory(path)
        }
    }

    @Transaction
    open suspend fun addToVaultWithMigration(
        originalPath: String,
        newPath: String,
        relativePath: String,
        addedAt: Long,
    ) {
        insertVaultFile(relativePath, originalPath, addedAt)
        val single = getByFilePath(originalPath)
        if (single != null) {
            updateFilePath(originalPath, newPath)
        } else {
            updateDirectoryPath(originalPath, newPath)
        }
    }

    @Transaction
    open suspend fun addFolderToVaultWithMigration(
        originalPath: String,
        newPath: String,
        relativePath: String,
        subFileRelativePaths: List<Pair<String, String>>,
        addedAt: Long,
    ) {
        insertVaultFile(relativePath, originalPath, addedAt)
        for ((subRelPath, subOrigPath) in subFileRelativePaths) {
            insertVaultFile(subRelPath, subOrigPath, addedAt)
        }
        updateDirectoryPath(originalPath, newPath)
    }

    @Transaction
    open suspend fun restoreFromVaultWithMigration(vaultPath: String, originalPath: String) {
        val single = getByFilePath(vaultPath)
        if (single != null) {
            updateFilePath(vaultPath, originalPath)
        } else {
            updateDirectoryPath(vaultPath, originalPath)
        }
    }
}
