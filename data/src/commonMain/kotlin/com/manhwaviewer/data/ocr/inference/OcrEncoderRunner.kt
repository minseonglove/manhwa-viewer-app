package com.manhwaviewer.data.ocr.inference

expect class OcrEncoderRunner {
    fun initialize()
    fun run(pixelValues: FloatArray): FloatArray
    fun close()
}
