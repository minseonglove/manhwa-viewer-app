package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.VaultFileRepository
import kotlinx.coroutines.flow.Flow

class GetVaultTotalCountUseCase(
    private val repository: VaultFileRepository,
) {
    operator fun invoke(): Flow<Int> = repository.getTotalCount()
}
