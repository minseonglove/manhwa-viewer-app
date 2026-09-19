package com.manhwaviewer.data.ocr.inference

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import java.io.File
import java.nio.FloatBuffer

actual class YoloRunner(
    private val context: Context,
    private val memory: OrtMemoryOptions = OrtMemoryOptions.NO_ARENA,
) {
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var runOptions: OrtSession.RunOptions? = null

    actual fun initialize() {
        val environment = memory.environment(INTRA_OP_THREADS)
        ortEnv = environment
        runOptions = memory.newRunOptions()
        session = OrtSession.SessionOptions().use { options ->
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            memory.configure(options, INTRA_OP_THREADS)
            environment.createSession(getModelPath(), options)
        }
    }

    actual fun run(imageData: FloatArray): FloatArray {
        val inputSize = MangaOcrConfig.YOLO_INPUT_SIZE
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(imageData), shape)
        val feeds = mapOf("images" to tensor)
        val options = runOptions
        val results = if (options == null) session!!.run(feeds) else session!!.run(feeds, options)
        val outputTensor = results.get("output0").get() as OnnxTensor
        val outputBuffer = outputTensor.floatBuffer
        val totalElements = MangaOcrConfig.YOLO_MAX_DETECTIONS * 6
        val output = FloatArray(totalElements) { outputBuffer.get(it) }
        results.close()
        tensor.close()
        return output
    }

    actual fun close() {
        session?.close(); session = null
        runOptions?.close(); runOptions = null
        ortEnv = null
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

    private companion object {
        /** `HayaiOnnxGraphs` 가 같은 기기에서 4 를 고른 측정을 따른다. YOLO 단독으로 재지는 않았다. */
        const val INTRA_OP_THREADS = 4
    }
}
