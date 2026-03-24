package com.manhwaviewer.data.ocr

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class AndroidMangaOcrEngineTest {

    companion object {
        private const val TAG = "MangaOcrTest"
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun recognizesJapaneseTextFromMangaImage() = runTest {
        val imageBytes = InstrumentationRegistry.getInstrumentation()
            .context.assets.open("test/00.jpg").readBytes()
        Log.d(TAG, "Image loaded: ${imageBytes.size} bytes")

        val engine = AndroidMangaOcrEngine(context)
        try {
            val startTime = System.currentTimeMillis()
            val result = engine.recognizeText(imageBytes)
            val elapsed = System.currentTimeMillis() - startTime

            Log.d(TAG, "=== OCR Result ===")
            Log.d(TAG, "Text: \"$result\"")
            Log.d(TAG, "Time: ${elapsed}ms")
            Log.d(TAG, "==================")

            assertTrue(
                "OCR result should contain '素直にあやまるしか', but was: \"$result\"",
                result.contains("素直に") && result.contains("あやまるしか"),
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun printsOcrResultForImage04() = runTest {
        printOcrResult("test/04.jpg")
    }

    @Test
    fun printsOcrResultForImage02() = runTest {
        printOcrResult("test/02.jpeg")
    }

    @Test
    fun detectsAndRecognizesWithCtd_02() = runTest {
        runPageOcr("test/02.jpeg")
    }

    @Test
    fun detectsAndRecognizesWithCtd_01() = runTest {
        runPageOcr("test/01.jpeg")
    }

    @Test
    fun detectsAndRecognizesWithCtd_03() = runTest {
        runPageOcr("test/03.jpeg")
    }

    private suspend fun runPageOcr(assetPath: String) {
        val fileName = assetPath.substringAfterLast("/")
        val imageBytes = InstrumentationRegistry.getInstrumentation()
            .context.assets.open(assetPath).readBytes()
        Log.d(TAG, "[$fileName CTD+OCR] Image loaded: ${imageBytes.size} bytes")

        val detector = CtdTextDetector(context)
        val repo = TextDetectionRepositoryImpl(
            detector,
            { AndroidMangaOcrEngine(context) },
            MlKitTextRecognizer(),
        )

        val startTime = System.currentTimeMillis()
        val blocks = repo.detectAndRecognize(imageBytes)
        val elapsed = System.currentTimeMillis() - startTime

        Log.d(TAG, "=== CTD Page OCR ($fileName) ===")
        Log.d(TAG, "Detected ${blocks.size} text blocks in ${elapsed}ms")
        blocks.forEachIndexed { i, block ->
            Log.d(TAG, "  [$i] (${block.x},${block.y} ${block.width}x${block.height}) \"${block.text}\"")
        }
        Log.d(TAG, "================================")

        assertFalse("Should detect at least one text block from $fileName", blocks.isEmpty())
    }

    private suspend fun printOcrResult(assetPath: String) {
        val imageBytes = InstrumentationRegistry.getInstrumentation()
            .context.assets.open(assetPath).readBytes()
        val fileName = assetPath.substringAfterLast("/")
        Log.d(TAG, "[$fileName] Image loaded: ${imageBytes.size} bytes")

        val engine = AndroidMangaOcrEngine(context)
        try {
            val startTime = System.currentTimeMillis()
            val result = engine.recognizeText(imageBytes)
            val elapsed = System.currentTimeMillis() - startTime

            Log.d(TAG, "=== OCR Result ($fileName) ===")
            Log.d(TAG, "Text: \"$result\"")
            Log.d(TAG, "Time: ${elapsed}ms")
            Log.d(TAG, "==============================")
        } finally {
            engine.close()
        }
    }
}
