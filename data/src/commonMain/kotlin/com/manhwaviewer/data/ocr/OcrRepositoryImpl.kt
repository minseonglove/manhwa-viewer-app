package com.manhwaviewer.data.ocr

import com.manhwaviewer.domain.repository.OcrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrRepositoryImpl(
    private val engineProvider: () -> MangaOcrEngine,
) : OcrRepository {
    override suspend fun recognizeText(imageBytes: ByteArray): String =
        withContext(Dispatchers.Default) {
            val engine = engineProvider()
            try {
                engine.recognizeText(imageBytes)
            } finally {
                engine.close()
            }
        }
}
