package com.manhwaviewer.data.ocr

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
    fun closesEngineAfterRecognition() = runTest {
        val engine = FakeMangaOcrEngine("テスト")
        val repo = OcrRepositoryImpl(engine)
        repo.recognizeText(byteArrayOf(1, 2, 3))
        assertTrue(engine.closed)
    }

    @Test
    fun closesEngineEvenOnError() = runTest {
        val engine = FailingMangaOcrEngine()
        val repo = OcrRepositoryImpl(engine)
        try {
            repo.recognizeText(byteArrayOf(1, 2, 3))
        } catch (_: RuntimeException) {
            // expected
        }
        assertTrue(engine.closed)
    }
}

private class FakeMangaOcrEngine(private val result: String) : MangaOcrEngine {
    var closed = false
        private set

    override suspend fun recognizeText(imageBytes: ByteArray): String = result

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

    override fun close() {
        closed = true
    }
}
