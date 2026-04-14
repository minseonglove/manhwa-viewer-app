package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.PlatformImage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManagedOcrEngine(
    private val factory: () -> MangaOcrEngine,
) : MangaOcrEngine {

    private var engine: MangaOcrEngine? = null
    private val mutex = Mutex()

    private suspend fun getEngine(): MangaOcrEngine =
        mutex.withLock { engine ?: factory().also { engine = it } }

    override suspend fun recognizeText(imageBytes: ByteArray): String =
        getEngine().recognizeText(imageBytes)

    override suspend fun recognizeText(image: PlatformImage): String =
        getEngine().recognizeText(image)

    override fun close() {
        engine?.close()
        engine = null
    }
}
