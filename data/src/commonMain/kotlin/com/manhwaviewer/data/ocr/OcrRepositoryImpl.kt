package com.manhwaviewer.data.ocr

import com.manhwaviewer.domain.repository.OcrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrRepositoryImpl(
    private val engine: MangaOcrEngine,
) : OcrRepository {
    override suspend fun recognizeText(imageBytes: ByteArray): String =
        withContext(Dispatchers.Default) {
            engine.recognizeText(imageBytes)
        }
}
