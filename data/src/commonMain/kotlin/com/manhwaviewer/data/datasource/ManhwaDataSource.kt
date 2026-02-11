package com.manhwaviewer.data.datasource

import com.manhwaviewer.domain.entity.Manhwa

interface ManhwaDataSource {
    suspend fun getManhwaList(): List<Manhwa>
    suspend fun getManhwaById(id: Long): Manhwa?
}
