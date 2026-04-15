package com.manhwaviewer.presentation.fake

import com.manhwaviewer.domain.entity.VaultFile
import com.manhwaviewer.domain.repository.VaultFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVaultFileRepository : VaultFileRepository {
    val totalCountFlow = MutableStateFlow(0)
    val filesFlow = MutableStateFlow<List<VaultFile>>(emptyList())

    override fun getFilesInDirectory(prefix: String): Flow<List<VaultFile>> = filesFlow
    override fun getTotalCount(): Flow<Int> = totalCountFlow
    override suspend fun addToVault(relativePath: String, originalPath: String): Long = 0L
    override suspend fun getByRelativePath(relativePath: String): VaultFile? = null
    override suspend fun deleteByRelativePathPrefix(prefix: String) {}
}
