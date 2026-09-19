package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.PlatformImage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrRepositoryImplTest {

    @Test
    fun delegatesToEngineAndReturnsResult() = runTest {
        val repo = OcrRepositoryImpl(FakeMangaOcrEngine("素直にあやまるしか"))
        val result = repo.recognizeText(byteArrayOf(1, 2, 3))
        assertEquals("素直にあやまるしか", result)
    }

    @Test
    fun doesNotCloseEngine() = runTest {
        val engine = FakeMangaOcrEngine("テスト")
        val repo = OcrRepositoryImpl(engine)
        repo.recognizeText(byteArrayOf(1, 2, 3))
        assertTrue(!engine.closed, "OcrRepositoryImpl should not close the engine — lifecycle is managed externally")
    }

    @Test
    fun propagatesEngineError() = runTest {
        val engine = FailingMangaOcrEngine()
        val repo = OcrRepositoryImpl(engine)
        var thrown = false
        try {
            repo.recognizeText(byteArrayOf(1, 2, 3))
        } catch (_: RuntimeException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}

private class FakeMangaOcrEngine(private val result: String) : MangaOcrEngine {
    var closed = false
        private set

    override suspend fun recognizeText(imageBytes: ByteArray): String = result

    override suspend fun recognizeText(image: PlatformImage): String = result

    override fun close() {
        closed = true
    }
}

private class FailingMangaOcrEngine : MangaOcrEngine {
    var closed = false
        private set

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        throw RuntimeException("OCR failed")
    }

    override suspend fun recognizeText(image: PlatformImage): String {
        throw RuntimeException("OCR failed")
    }

    override fun close() {
        closed = true
    }
}
