package com.manhwaviewer.data.ocr

data class DetectedBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
    val isJapanese: Boolean,
)
