package com.manhwaviewer.data.ocr.inference

expect class OcrDecoderRunner {
    fun initialize()
    fun run(inputIds: IntArray, seqLen: Int, encoderOutput: FloatArray): FloatArray
    fun close()
}
