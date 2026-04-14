package com.manhwaviewer.data.repository

import com.manhwaviewer.data.db.VaultFileDao
import com.manhwaviewer.data.db.toDomain
import com.manhwaviewer.domain.entity.VaultFile
import com.manhwaviewer.domain.repository.VaultFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VaultFileRepositoryImpl(
    private val dao: VaultFileDao,
) : VaultFileRepository {

    override fun getFilesInDirectory(prefix: String): Flow<List<VaultFile>> =
        dao.getFilesInDirectory(prefix).map { entities -> entities.map { it.toDomain() } }

    override fun getTotalCount(): Flow<Int> = dao.getTotalCount()
}
