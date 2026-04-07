package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.repository.TextDetectionRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RecognizePageTextUseCase(
    private val repository: TextDetectionRepository,
) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(imageBytes: ByteArray): List<TextBlock> {
        val start = Clock.System.now()
        val results = repository.detectAndRecognize(imageBytes)
        val elapsed = Clock.System.now() - start
        println("OCR | ${elapsed.inWholeMilliseconds}ms, ${results.size} blocks, imageSize=${imageBytes.size}")
        return results
    }
}
