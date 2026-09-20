package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.PlatformImage

interface MangaOcrEngine {
    suspend fun recognizeText(imageBytes: ByteArray): String
    suspend fun recognizeText(image: PlatformImage): String
    fun close()
}

object MangaOcrConfig {
    const val YOLO_INPUT_SIZE = 640
    const val YOLO_MAX_DETECTIONS = 300
}
