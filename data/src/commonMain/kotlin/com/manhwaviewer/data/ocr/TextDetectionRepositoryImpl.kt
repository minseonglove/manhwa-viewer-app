package com.manhwaviewer.data.ocr

import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.repository.TextDetectionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock

class TextDetectionRepositoryImpl(
    private val textDetector: TextDetector,
    private val ocrEngineProvider: () -> MangaOcrEngine,
    private val platformTextRecognizer: PlatformTextRecognizer,
) : TextDetectionRepository {

    companion object {
        private const val OVERLAP_THRESHOLD = 0.3f
    }

    override suspend fun detectAndRecognize(imageBytes: ByteArray): List<TextBlock> =
        coroutineScope {
            val totalStart = now()

            // 1. CTD + Platform 병렬 검출
            val ctdDeferred = async {
                val start = now()
                val regions = textDetector.detect(imageBytes)
                println("PageOCR | CTD detection: ${now() - start}ms (${regions.size} regions)")
                regions
            }
            val horizontalDeferred = async {
                val start = now()
                val results = platformTextRecognizer.detectHorizontalText(imageBytes)
                println("PageOCR | Platform horizontal: ${now() - start}ms (${results.size} regions)")
                results
            }

            val ctdRegions = ctdDeferred.await()
            val horizontalResults = horizontalDeferred.await()

            // 2. 가로 결과 중 CTD와 겹치지 않는 것만 채택
            val filteredHorizontal = horizontalResults.filter { hr ->
                ctdRegions.none { ctd -> iou(hr, ctd) > OVERLAP_THRESHOLD }
            }
            println("PageOCR | After overlap filter: ${filteredHorizontal.size} horizontal kept")

            // 3. 가로 텍스트 → 플랫폼 인식 결과 그대로 사용
            val horizontalBlocks = filteredHorizontal.mapIndexed { i, region ->
                println("PageOCR |   H[$i] (${region.x},${region.y} ${region.width}x${region.height}): \"${region.text}\"")
                TextBlock(region.x, region.y, region.width, region.height, region.text)
            }

            // 4. CTD 영역 → MangaOCR
            val verticalBlocks = if (ctdRegions.isNotEmpty()) {
                val engine = ocrEngineProvider()
                try {
                    ctdRegions.mapIndexed { i, region ->
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

    private fun iou(hr: RecognizedHorizontalRegion, ctd: DetectedRegion): Float {
        val ix1 = maxOf(hr.x, ctd.x)
        val iy1 = maxOf(hr.y, ctd.y)
        val ix2 = minOf(hr.x + hr.width, ctd.x + ctd.width)
        val iy2 = minOf(hr.y + hr.height, ctd.y + ctd.height)
        val intersection = maxOf(0, ix2 - ix1) * maxOf(0, iy2 - iy1)
        if (intersection == 0) return 0f
        val union = hr.width * hr.height + ctd.width * ctd.height - intersection
        return intersection.toFloat() / union.toFloat()
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
