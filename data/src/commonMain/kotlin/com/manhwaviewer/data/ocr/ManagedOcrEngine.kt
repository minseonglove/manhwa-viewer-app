package com.manhwaviewer.data.ocr

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManagedOcrEngine(
    private val factory: () -> MangaOcrEngine,
) : MangaOcrEngine {

    private var engine: MangaOcrEngine? = null
    private val mutex = Mutex()

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        val e = mutex.withLock {
            engine ?: factory().also { engine = it }
        }
        return e.recognizeText(imageBytes)
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
