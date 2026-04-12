package com.manhwaviewer.data.ocr

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDetectorComparisonTest {

    companion object {
        private const val TAG = "DetectorCompare"
        private const val TEST_IMAGE = "test/06.png"
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun comparesYoloVsYoloMlKit() = runTest {
        val imageBytes = InstrumentationRegistry.getInstrumentation()
            .context.assets.open(TEST_IMAGE).readBytes()
        val fileName = TEST_IMAGE.substringAfterLast("/")

        // === 1. YOLO pipeline ===
        val yoloPipeline = TextDetectionRepositoryImpl(
            YoloTextDetector(context),
            verticalOcr = AndroidMangaOcrEngine(context),
        )
        val yoloStart = System.currentTimeMillis()
        val yoloBlocks = yoloPipeline.detectAndRecognize(imageBytes)
        val yoloElapsed = System.currentTimeMillis() - yoloStart

        Log.d(TAG, "=== YOLO Pipeline ($fileName) ===")
        Log.d(TAG, "Time: ${yoloElapsed}ms | Blocks: ${yoloBlocks.size}")
        yoloBlocks.forEachIndexed { i, b ->
            Log.d(TAG, "  [$i] (${b.x},${b.y} ${b.width}x${b.height}) \"${b.text}\"")
        }

        // === 2. YOLO + MLKit hybrid pipeline ===
        val hybridPipeline = TextDetectionRepositoryImpl(
            YoloTextDetector(context),
            verticalOcr = AndroidMangaOcrEngine(context),
            platformRecognizer = MlKitTextRecognizer(),
        )
        val hybridStart = System.currentTimeMillis()
        val hybridBlocks = hybridPipeline.detectAndRecognize(imageBytes)
        val hybridElapsed = System.currentTimeMillis() - hybridStart

        Log.d(TAG, "=== YOLO + MLKit Hybrid Pipeline ($fileName) ===")
        Log.d(TAG, "Time: ${hybridElapsed}ms | Blocks: ${hybridBlocks.size}")
        hybridBlocks.forEachIndexed { i, b ->
            Log.d(TAG, "  [$i] (${b.x},${b.y} ${b.width}x${b.height}) \"${b.text}\"")
        }

        // === 비교 요약 ===
        Log.d(TAG, "=== COMPARISON SUMMARY ($fileName) ===")
        Log.d(TAG, "YOLO:         ${yoloElapsed}ms, ${yoloBlocks.size} blocks")
        Log.d(TAG, "YOLO+MLKit:   ${hybridElapsed}ms, ${hybridBlocks.size} blocks")
        Log.d(TAG, "===================================")

        assertTrue("YOLO should detect blocks", yoloBlocks.isNotEmpty())
        assertTrue("YOLO+MLKit should detect blocks", hybridBlocks.isNotEmpty())
    }
}
