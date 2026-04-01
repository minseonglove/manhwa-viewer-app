package com.manhwaviewer.data.ocr

import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.repository.TextDetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class TextDetectionRepositoryImpl(
    private val textDetector: TextDetector,
    private val verticalOcrProvider: () -> MangaOcrEngine,
    private val horizontalOcrProvider: () -> MangaOcrEngine,
) : TextDetectionRepository {

    companion object {
        private const val HORIZONTAL_RATIO = 2.0f
    }

    override suspend fun detectAndRecognize(imageBytes: ByteArray): List<TextBlock> =
        withContext(Dispatchers.Default) {
            val totalStart = now()

            // 1. 텍스트 영역 검출
            val start = now()
            val allRegions = textDetector.detect(imageBytes)
            println("PageOCR | Detection: ${now() - start}ms (${allRegions.size} regions)")

            // 2. 가로/세로 분리 (width/height >= HORIZONTAL_RATIO → 가로)
            val (horizontal, vertical) = allRegions.partition {
                it.height > 0 && it.width.toFloat() / it.height >= HORIZONTAL_RATIO
            }
            println("PageOCR | Split: ${horizontal.size} horizontal, ${vertical.size} vertical")

            // 3. 가로 텍스트 → PaddleOCR rec
            val horizontalBlocks = if (horizontal.isNotEmpty()) {
                val engine = horizontalOcrProvider()
                try {
                    horizontal.mapIndexed { i, region ->
                        val ocrStart = now()
                        val text = engine.recognizeText(region.croppedImageBytes)
                        println("PageOCR |   H[$i] (${region.x},${region.y} ${region.width}x${region.height}): ${now() - ocrStart}ms → \"$text\"")
                        TextBlock(region.x, region.y, region.width, region.height, text)
                    }
                } finally {
                    engine.close()
                }
            } else {
                emptyList()
            }

            // 4. 세로 텍스트 → MangaOCR
            val verticalBlocks = if (vertical.isNotEmpty()) {
                val engine = verticalOcrProvider()
                try {
                    vertical.mapIndexed { i, region ->
                        val ocrStart = now()
                        val text = engine.recognizeText(region.croppedImageBytes)
                        println("PageOCR |   V[$i] (${region.x},${region.y} ${region.width}x${region.height}): ${now() - ocrStart}ms → \"$text\"")
                        TextBlock(region.x, region.y, region.width, region.height, text)
                    }
                } finally {
                    engine.close()
                }
            } else {
                emptyList()
            }

            val result = (horizontalBlocks + verticalBlocks).sortedWith(compareBy({ it.y }, { it.x }))
            println("PageOCR | Total: ${now() - totalStart}ms (${result.size} blocks)")
            result
        }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
