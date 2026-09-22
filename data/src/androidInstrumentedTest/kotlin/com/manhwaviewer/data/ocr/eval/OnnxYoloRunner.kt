package com.manhwaviewer.data.ocr.eval

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.inference.OrtMemoryOptions
import java.io.File
import java.nio.FloatBuffer
import org.junit.Assume.assumeTrue

/**
 * The detector as the app ran it before LiteRT: the fp16 YOLO ONNX graph on ORT's CPU, 4 intra-op
 * threads. Kept for measurements that compare against that baseline.
 *
 * The graph is no longer in the APK; runs push it to `filesDir/ocr-eval/onnx-yolo/`.
 */
class OnnxYoloRunner(file: File, memory: OrtMemoryOptions = OrtMemoryOptions.NO_ARENA) : AutoCloseable {

    private val env: OrtEnvironment = memory.environment(INTRA_OP_THREADS)
    private val runOptions: OrtSession.RunOptions? = memory.newRunOptions()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        memory.configure(options, INTRA_OP_THREADS)
        env.createSession(file.absolutePath, options)
    }

    fun run(imageData: FloatArray): FloatArray {
        val size = MangaOcrConfig.YOLO_INPUT_SIZE.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(imageData), longArrayOf(1, 3, size, size)).use { tensor ->
            val feeds = mapOf("images" to tensor)
            val results = if (runOptions == null) session.run(feeds) else session.run(feeds, runOptions)
            return results.use {
                val buffer = (it.get("output0").get() as OnnxTensor).floatBuffer
                FloatArray(MangaOcrConfig.YOLO_MAX_DETECTIONS * 6) { i -> buffer.get(i) }
            }
        }
    }

    override fun close() {
        session.close()
        runOptions?.close()
    }

    companion object {
        private const val INTRA_OP_THREADS = 4

        fun pushedFile(context: Context): File =
            File(File(File(context.filesDir, "ocr-eval"), "onnx-yolo"), "manga_panel_detector_fp16.onnx").also {
                assumeTrue("No ONNX YOLO baseline at ${it.absolutePath}", it.isFile)
            }
    }
}
