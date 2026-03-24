package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.repository.TextDetectionRepository

class RecognizePageTextUseCase(
    private val repository: TextDetectionRepository,
) {
    suspend operator fun invoke(imageBytes: ByteArray): List<TextBlock> {
        return repository.detectAndRecognize(imageBytes)
    }
}
