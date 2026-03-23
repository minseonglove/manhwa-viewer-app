package com.manhwaviewer.data.repository

import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.domain.repository.ImageFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ImageFileRepositoryImpl : ImageFileRepository {

    override fun getAllImages(): Flow<List<ImageFile>> {
        // TODO: PHAsset 기반 구현
        return flowOf(emptyList())
    }
}
