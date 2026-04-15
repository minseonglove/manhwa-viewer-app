package com.manhwaviewer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultFileDao {
    @Query(
        "SELECT * FROM vault_files WHERE relativePath LIKE :prefix || '%' AND relativePath NOT LIKE :prefix || '%/%'",
    )
    fun getFilesInDirectory(prefix: String): Flow<List<VaultFileEntity>>

    @Query("SELECT COUNT(*) FROM vault_files")
    fun getTotalCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entity: VaultFileEntity): Long

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM vault_files WHERE relativePath = :relativePath LIMIT 1")
    suspend fun getByRelativePath(relativePath: String): VaultFileEntity?

    @Query("DELETE FROM vault_files WHERE relativePath = :prefix OR relativePath LIKE :prefix || '/%'")
    suspend fun deleteByRelativePathPrefix(prefix: String)
}
