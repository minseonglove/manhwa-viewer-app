package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.PlatformImage

data class DetectedRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val croppedImage: PlatformImage,
)

interface TextDetector {
    suspend fun detect(imageBytes: ByteArray): List<DetectedRegion>
}

data class RecognizedHorizontalRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String,
)

interface PlatformTextRecognizer {
    suspend fun recognizeText(imageBytes: ByteArray): String
    suspend fun detectHorizontalText(imageBytes: ByteArray): List<RecognizedHorizontalRegion>
}
