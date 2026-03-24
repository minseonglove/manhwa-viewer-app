package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.TextBlock

interface TextDetectionRepository {
    suspend fun detectAndRecognize(imageBytes: ByteArray): List<TextBlock>
}
