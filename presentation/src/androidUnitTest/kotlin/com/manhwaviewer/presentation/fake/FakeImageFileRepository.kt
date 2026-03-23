package com.manhwaviewer.presentation.fake

import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.domain.repository.ImageFileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class FakeImageFileRepository : ImageFileRepository {
    val allImagesFlow = MutableStateFlow<List<ImageFile>>(emptyList())

    override fun getAllImages(): Flow<List<ImageFile>> = allImagesFlow
}

fun makeImageFile(
    id: Long = 0,
    fileName: String = "test.jpg",
    filePath: String = "/storage/$fileName",
    mimeType: String = "image/jpeg",
    size: Long = 1024,
    dateModified: Instant = Instant.fromEpochMilliseconds(0),
    folderName: String = "Pictures",
) = ImageFile(
    id = id,
    filePath = filePath,
    fileName = fileName,
    mimeType = mimeType,
    size = size,
    dateModified = dateModified,
    folderName = folderName,
)
