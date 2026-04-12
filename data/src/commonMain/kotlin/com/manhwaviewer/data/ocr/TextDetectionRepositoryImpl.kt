package com.manhwaviewer.data.ocr

import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.repository.DetectionStage
import com.manhwaviewer.domain.repository.TextDetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class TextDetectionRepositoryImpl(
    private val textDetector: TextDetector,
    private val verticalOcr: MangaOcrEngine,
    private val platformRecognizer: PlatformTextRecognizer? = null,
) : TextDetectionRepository {

    companion object {
        private const val HORIZONTAL_RATIO = 2.0f
        private const val AMBIGUOUS_RATIO = 1.5f
        private const val MAX_CONCURRENT_OCR = 3
        private const val OVERLAP_IOU_THRESHOLD = 0.3f
    }

    private val ocrSemaphore = Semaphore(MAX_CONCURRENT_OCR)

    override suspend fun detectAndRecognize(imageBytes: ByteArray): List<TextBlock> =
        detectAndRecognizeInternal(imageBytes, onStageChanged = null)

    override suspend fun detectAndRecognize(
        imageBytes: ByteArray,
        onStageChanged: (DetectionStage) -> Unit,
    ): List<TextBlock> = detectAndRecognizeInternal(imageBytes, onStageChanged)

    private suspend fun detectAndRecognizeInternal(
        imageBytes: ByteArray,
        onStageChanged: ((DetectionStage) -> Unit)?,
    ): List<TextBlock> = withContext(Dispatchers.Default) {
        val totalStart = now()

        coroutineScope {
            // 1. YOLO 검출 + MLKit 가로 텍스트 인식을 병렬 실행
            onStageChanged?.invoke(DetectionStage.DETECTING)

            val detectionDeferred = async {
                val start = now()
                val regions = textDetector.detect(imageBytes)
                println("PageOCR | Detection: ${now() - start}ms (${regions.size} regions)")
                regions
            }

            val horizontalTextDeferred = platformRecognizer?.let {
                async {
                    val start = now()
                    val results = it.detectHorizontalText(imageBytes)
                    println("PageOCR | Platform horizontal: ${now() - start}ms (${results.size} regions)")
                    results
                }
            }

            val allRegions = detectionDeferred.await()
            val platformHorizontal = horizontalTextDeferred?.await().orEmpty()

            // 2. 플랫폼 인식 결과를 TextBlock으로 변환
            onStageChanged?.invoke(DetectionStage.CROPPING)

            val platformBlocks = platformHorizontal.map { r ->
                TextBlock(r.x, r.y, r.width, r.height, r.text)
            }

            // 3. YOLO 검출 영역을 방향성별로 분류
            //    - ratio >= 2.0: 가로 확정 → 제외
            //    - ratio 1.5~2.0: 애매 → MLKit 겹치면 MLKit 우선, 아니면 MangaOCR
            //    - ratio < 1.5: 세로/정사각형 → YOLO 우선
            val (horizontalRegions, nonHorizontalRegions) = allRegions.partition { region ->
                region.height > 0 &&
                    region.width.toFloat() / region.height >= HORIZONTAL_RATIO
            }
            val (ambiguousRegions, verticalRegions) = nonHorizontalRegions.partition { region ->
                region.height > 0 &&
                    region.width.toFloat() / region.height >= AMBIGUOUS_RATIO
            }

            // 4. 플랫폼(MLKit) 결과 중 YOLO 세로 영역과 겹치는 것 제거 (YOLO 우선)
            val filteredPlatformBlocks = platformBlocks.filter { pb ->
                verticalRegions.none { region -> iou(region, pb) > OVERLAP_IOU_THRESHOLD }
            }

            // 5. 애매 영역 중 MLKit과 겹치지 않는 것만 MangaOCR로 처리
            val ambiguousOcrTargets = ambiguousRegions.filter { region ->
                platformBlocks.none { pb -> iou(region, pb) > OVERLAP_IOU_THRESHOLD }
            }

            val ocrTargets = verticalRegions + ambiguousOcrTargets
            val overlappingCount = platformBlocks.size - filteredPlatformBlocks.size
            println("PageOCR | OCR targets: ${ocrTargets.size} vertical(${verticalRegions.size}) + ambiguous(${ambiguousOcrTargets.size}/${ambiguousRegions.size}) (skipped ${horizontalRegions.size} horizontal)")
            if (overlappingCount > 0) {
                println("PageOCR | Removed $overlappingCount platform result(s) overlapping with YOLO vertical regions")
            }

            // 5. 세로 영역만 MangaOCR로 인식
            onStageChanged?.invoke(DetectionStage.RECOGNIZING)

            val verticalResults = ocrTargets.mapIndexed { i, region ->
                async {
                    ocrSemaphore.withPermit {
                        val ocrStart = now()
                        val text = verticalOcr.recognizeText(region.croppedImageBytes)
                        println("PageOCR |   V[$i] (${region.x},${region.y} ${region.width}x${region.height}): ${now() - ocrStart}ms → \"$text\"")
                        TextBlock(region.x, region.y, region.width, region.height, text)
                    }
                }
            }

            // 6. 병합, 필터, 정렬
            val result = (filteredPlatformBlocks + verticalResults.awaitAll())
                .filter { it.text.containsCjkOrKana() }
                .sortedWith(compareBy({ it.y }, { it.x }))
            println("PageOCR | Total: ${now() - totalStart}ms (${result.size} blocks)")
            result
        }
    }

    private fun iou(region: DetectedRegion, block: TextBlock): Float {
        val ix1 = maxOf(region.x, block.x)
        val iy1 = maxOf(region.y, block.y)
        val ix2 = minOf(region.x + region.width, block.x + block.width)
        val iy2 = minOf(region.y + region.height, block.y + block.height)
        val intersection = maxOf(0, ix2 - ix1) * maxOf(0, iy2 - iy1)
        if (intersection == 0) return 0f
        val union = region.width * region.height + block.width * block.height - intersection
        return intersection.toFloat() / union.toFloat()
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private fun String.containsCjkOrKana(): Boolean =
        any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' }
}
