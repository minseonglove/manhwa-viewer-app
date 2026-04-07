package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.TextBlock

enum class DetectionStage { DETECTING, CROPPING, RECOGNIZING }

interface TextDetectionRepository {
    suspend fun detectAndRecognize(imageBytes: ByteArray): List<TextBlock>

    suspend fun detectAndRecognize(
        imageBytes: ByteArray,
        onStageChanged: (DetectionStage) -> Unit,
    ): List<TextBlock> = detectAndRecognize(imageBytes)
}
