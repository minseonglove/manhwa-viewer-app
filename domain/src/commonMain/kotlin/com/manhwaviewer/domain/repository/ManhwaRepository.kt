package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.Manhwa

interface ManhwaRepository {
    suspend fun getManhwaList(): List<Manhwa>
    suspend fun getManhwaById(id: Long): Manhwa?
}
