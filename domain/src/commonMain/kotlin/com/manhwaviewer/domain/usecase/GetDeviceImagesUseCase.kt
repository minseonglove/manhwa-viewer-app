package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.domain.repository.ImageFileRepository
import kotlinx.coroutines.flow.Flow

class GetDeviceImagesUseCase(
    private val repository: ImageFileRepository,
) {
    operator fun invoke(): Flow<List<ImageFile>> = repository.getAllImages()
}
