package com.manhwaviewer.data.ocr.inference

import android.content.Context
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import com.manhwaviewer.data.ocr.floatToHalf
import com.manhwaviewer.data.ocr.halfToFloat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class OcrEncoderRunner(private val context: Context) {
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    actual fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        session = ortEnv!!.createSession(getModelPath(), OrtSession.SessionOptions())
    }

    actual fun run(pixelValues: FloatArray): FloatArray {
        val env = ortEnv!!
        val size = MangaOcrConfig.IMAGE_SIZE
        val area = size * size
        // float32 → float16 ByteBuffer
        val buffer = ByteBuffer.allocateDirect(3 * area * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until 3 * area) {
            buffer.putShort(i * 2, floatToHalf(pixelValues[i]))
        }
        val shape = longArrayOf(1, 3, size.toLong(), size.toLong())
        val tensor = OnnxTensor.createTensor(env, buffer, shape, OnnxJavaType.FLOAT16)
        val results = session!!.run(mapOf("pixel_values" to tensor))
        val outputTensor = results.get(0) as OnnxTensor
        val shortBuffer = outputTensor.shortBuffer
        // float16 → float32
        val output = FloatArray(shortBuffer.remaining()) { i -> halfToFloat(shortBuffer.get(i)) }
        results.close()
        tensor.close()
        return output
    }

    actual fun close() {
        session?.close(); session = null; ortEnv = null
    }

    private fun getModelPath(): String {
        val file = File(context.cacheDir, PlatformModelPaths.ENCODER_MODEL_PATH)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "${PlatformModelPaths.ENCODER_MODEL_PATH}.tmp")
            context.assets.open(PlatformModelPaths.ENCODER_MODEL_PATH).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(file)
        }
        return file.absolutePath
    }
}
