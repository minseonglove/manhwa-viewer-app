package com.manhwaviewer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class AndroidPaddleOcrRecognizer(
    private val context: Context,
) : MangaOcrEngine {

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private lateinit var dictionary: PaddleOcrDictionary
    private var initialized = false

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        if (!initialized) {
            initialize()
            initialized = true
        }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return ""

        val (inputBuffer, inputW) = preprocessForRec(bitmap)
        bitmap.recycle()

        val shape = longArrayOf(1, 3, PaddleOcrConfig.REC_IMAGE_HEIGHT.toLong(), inputW.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape)

        val results = session!!.run(mapOf("x" to tensor))
        val outputTensor = results.get(0) as OnnxTensor
        val outputInfo = outputTensor.info.shape
        val seqLen = outputInfo[1].toInt()
        val vocabSize = outputInfo[2].toInt()

        val outputBuffer = outputTensor.floatBuffer
        val outputArray = FloatArray(outputBuffer.remaining())
        outputBuffer.get(outputArray)

        results.close()
        tensor.close()

        val indices = CtcDecoder.greedyDecode(outputArray, seqLen, vocabSize)
        return dictionary.decode(indices)
    }

    private fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        session = ortEnv!!.createSession(getModelPath(PaddleOcrConfig.REC_MODEL), options)

        val dictLines = context.assets.open(PaddleOcrConfig.DICT_FILE).bufferedReader().readLines()
        dictionary = PaddleOcrDictionary(dictLines)
    }

    private fun preprocessForRec(bitmap: Bitmap): Pair<FloatBuffer, Int> {
        val targetH = PaddleOcrConfig.REC_IMAGE_HEIGHT
        val ratio = targetH.toFloat() / bitmap.height
        var targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        if (targetW > PaddleOcrConfig.REC_MAX_WIDTH) {
            targetW = PaddleOcrConfig.REC_MAX_WIDTH
        }

        val resized = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

        val pixels = IntArray(targetH * targetW)
        resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        if (resized !== bitmap) resized.recycle()

        val area = targetH * targetW
        val buffer = ByteBuffer.allocateDirect(3 * area * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.put(i, (pixel shr 16 and 0xFF) / 127.5f - 1.0f)
            buffer.put(area + i, (pixel shr 8 and 0xFF) / 127.5f - 1.0f)
            buffer.put(2 * area + i, (pixel and 0xFF) / 127.5f - 1.0f)
        }

        return buffer to targetW
    }

    private fun getModelPath(assetName: String): String {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return file.absolutePath
    }

    override fun close() {
        session?.close()
        session = null
        ortEnv = null
        initialized = false
    }
}
