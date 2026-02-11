package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.Manhwa
import com.manhwaviewer.domain.repository.ManhwaRepository

class GetManhwaListUseCase(
    private val repository: ManhwaRepository,
) {
    suspend operator fun invoke(): List<Manhwa> {
        return repository.getManhwaList()
    }
}
