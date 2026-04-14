package com.manhwaviewer.data.ocr.inference

expect class YoloRunner {
    fun initialize()
    fun run(imageData: FloatArray): FloatArray
    fun close()
}
