package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.ImageFile
import kotlinx.coroutines.flow.Flow

interface ImageFileRepository {
    fun getAllImages(): Flow<List<ImageFile>>
}
