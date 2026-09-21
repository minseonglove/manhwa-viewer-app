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
import kotlin.time.Clock

class TextDetectionRepositoryImpl(
    private val textDetector: TextDetector,
    private val verticalOcr: MangaOcrEngine,
    private val platformRecognizer: PlatformTextRecognizer? = null,
    maxConcurrentOcr: Int = MAX_CONCURRENT_OCR,
) : TextDetectionRepository {

    companion object {
        private const val MAX_CONCURRENT_OCR = 4
    }

    private val ocrSemaphore = Semaphore(maxConcurrentOcr)

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
            // 1. YOLO 검출과 플랫폼 인식기(있을 때)의 가로 텍스트 인식을 병렬 실행
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

            // 3. 영역마다 누가 읽을지 정한다
            val routing = routeRegions(
                regions = allRegions.map { RegionBox(it.x, it.y, it.width, it.height) },
                platformBlocks = platformBlocks.map { RegionBox(it.x, it.y, it.width, it.height) },
                platformAvailable = platformRecognizer != null,
            )
            val filteredPlatformBlocks = routing.keptPlatformBlocks.map { platformBlocks[it] }
            val ocrTargets = routing.ocrTargets.map { allRegions[it] }
            println("PageOCR | OCR targets: ${ocrTargets.size} of ${allRegions.size} (vertical ${routing.verticalCount}, ambiguous ${routing.ambiguousCount}, skipped ${routing.skippedHorizontal} horizontal)")
            val overlappingCount = platformBlocks.size - filteredPlatformBlocks.size
            if (overlappingCount > 0) {
                println("PageOCR | Removed $overlappingCount platform result(s) overlapping with YOLO vertical regions")
            }

            // 4. OCR 모델로 인식
            onStageChanged?.invoke(DetectionStage.RECOGNIZING)

            val verticalResults = ocrTargets.mapIndexed { i, region ->
                async {
                    ocrSemaphore.withPermit {
                        val ocrStart = now()
                        val text = verticalOcr.recognizeText(region.croppedImage)
                        println("PageOCR |   V[$i] (${region.x},${region.y} ${region.width}x${region.height}): ${now() - ocrStart}ms → \"$text\"")
                        TextBlock(region.x, region.y, region.width, region.height, text)
                    }
                }
            }

            // 5. 병합, 필터, 정렬
            val result = (filteredPlatformBlocks + verticalResults.awaitAll())
                .filter { it.text.containsCjkOrKana() }
                .sortedWith(compareBy({ it.y }, { it.x }))
            println("PageOCR | Total: ${now() - totalStart}ms (${result.size} blocks)")
            result
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * 실제 의미 있는 일본어/한자 문자가 1개 이상 포함되어 있는지 확인한다.
     * CJK 구두점(・, ー, 。 등)만으로 이루어진 텍스트는 false를 반환한다.
     *
     * - Hiragana letters: U+3041–U+3096 (ぁ–ゖ, 구두점/기호 제외)
     * - Katakana letters: U+30A1–U+30FA (ァ–ヺ, ・ー 등 구두점 제외)
     * - CJK Unified Ideographs: U+4E00–U+9FFF
     * - CJK Extension A: U+3400–U+4DBF
     */
    private fun String.containsCjkOrKana(): Boolean =
        any { it in '\u3041'..'\u3096' || it in '\u30A1'..'\u30FA' || it in '\u4E00'..'\u9FFF' || it in '\u3400'..'\u4DBF' }
}
