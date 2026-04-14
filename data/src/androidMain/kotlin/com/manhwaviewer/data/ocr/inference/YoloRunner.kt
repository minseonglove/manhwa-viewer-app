package com.manhwaviewer.data.ocr.inference

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import java.io.File
import java.nio.FloatBuffer

actual class YoloRunner(private val context: Context) {
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    actual fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        session = ortEnv!!.createSession(getModelPath(), options)
    }

    actual fun run(imageData: FloatArray): FloatArray {
        val inputSize = MangaOcrConfig.YOLO_INPUT_SIZE
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(imageData), shape)
        val results = session!!.run(mapOf("images" to tensor))
        val outputTensor = results.get("output0").get() as OnnxTensor
        val outputBuffer = outputTensor.floatBuffer
        val totalElements = MangaOcrConfig.YOLO_MAX_DETECTIONS * 6
        val output = FloatArray(totalElements) { outputBuffer.get(it) }
        results.close()
        tensor.close()
        return output
    }

    actual fun close() {
        session?.close(); session = null; ortEnv = null
    }

    private fun getModelPath(): String {
        val file = File(context.cacheDir, PlatformModelPaths.YOLO_MODEL_PATH)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "${PlatformModelPaths.YOLO_MODEL_PATH}.tmp")
            context.assets.open(PlatformModelPaths.YOLO_MODEL_PATH).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(file)
        }
        return file.absolutePath
    }
}
