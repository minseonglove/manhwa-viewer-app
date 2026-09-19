package com.manhwaviewer.data.ocr.hayai

import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.image.PlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.getImageDimensions
import com.manhwaviewer.data.ocr.image.readRgbHwc

/**
 * Hayai behind the app's [MangaOcrEngine] contract.
 *
 * The app hands this a crop that the detector has already padded, so the only work left before
 * the decode loop is the part NaFlex leaves to the host: pick the patch grid that fits this
 * crop's aspect ratio inside the encoder's 256 patches, and resize to it.
 *
 * The graphs must already be open when the [HayaiOcrEngine] is handed over; [close] releases
 * them.
 */
class HayaiMangaOcrEngine(
    private val engine: HayaiOcrEngine,
) : MangaOcrEngine {

    override suspend fun recognizeText(imageBytes: ByteArray): String =
        recognizeText(decodeImage(imageBytes))

    override suspend fun recognizeText(image: PlatformImage): String {
        val (width, height) = getImageDimensions(image)
        return recognizeRgb(readRgbHwc(image), height, width)
    }

    /**
     * Recognise a crop already held as HWC uint8 RGB.
     *
     * Kept public so the grid choice and the resize can be driven without a platform image.
     * Anything measuring this OCR path must come through here; a second implementation of
     * these two steps would be measuring something the app does not run.
     */
    fun recognizeRgb(rgb: IntArray, height: Int, width: Int): String {
        val (targetHeight, targetWidth) = HayaiHost.scaledSize(height, width)
        val resized = HayaiHost.resizeRgb(rgb, height, width, targetHeight, targetWidth)
        return engine.recognize(
            FloatArray(resized.size) { resized[it].toFloat() },
            targetHeight / HayaiHost.PATCH_SIZE,
            targetWidth / HayaiHost.PATCH_SIZE,
        )
    }

    override fun close() {
        engine.close()
    }
}
