package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.VaultFile
import kotlinx.coroutines.flow.Flow

interface VaultFileRepository {
    fun getFilesInDirectory(prefix: String): Flow<List<VaultFile>>
    fun getTotalCount(): Flow<Int>
    suspend fun addToVault(relativePath: String, originalPath: String): Long
    suspend fun getByRelativePath(relativePath: String): VaultFile?
    suspend fun deleteByRelativePathPrefix(prefix: String)
}
