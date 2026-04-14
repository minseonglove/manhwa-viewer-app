package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.VaultFile
import com.manhwaviewer.domain.repository.VaultFileRepository
import kotlinx.coroutines.flow.Flow

class GetVaultDirectoryFilesUseCase(
    private val repository: VaultFileRepository,
) {
    operator fun invoke(prefix: String): Flow<List<VaultFile>> =
        repository.getFilesInDirectory(prefix)
}
