package com.manhwaviewer.data.repository

import com.manhwaviewer.domain.entity.Manhwa
import com.manhwaviewer.domain.repository.ManhwaRepository

class ManhwaRepositoryImpl : ManhwaRepository {
    override suspend fun getManhwaList(): List<Manhwa> {
        // TODO: Implement with actual data source
        return emptyList()
    }

    override suspend fun getManhwaById(id: Long): Manhwa? {
        // TODO: Implement with actual data source
        return null
    }
}
