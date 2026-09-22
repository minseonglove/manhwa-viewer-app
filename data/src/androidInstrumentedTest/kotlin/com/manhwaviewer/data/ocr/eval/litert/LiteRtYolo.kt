package com.manhwaviewer.data.ocr.eval.litert

import android.os.SystemClock
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import java.io.File

/**
 * The app's YOLO26n end2end detector converted to LiteRT (`yolo_detector_fp32.tflite`).
 *
 * Same contract as `YoloRunner`: NCHW (1, 3, 640, 640) in, (1, 300, 6) out. The conversion went
 * fp16 ONNX → fp32 ONNX → torch → LiteRT and was checked page by page against the ONNX original.
 */
class LiteRtYoloRunner(file: File, environment: Environment, config: LiteRtConfig) : AutoCloseable {

    val compileMs: Long

    private val model: CompiledModel
    private val input by lazy { model.createInputBuffer("args_0", SIGNATURE) }
    private val output by lazy { model.createOutputBuffer("output_0", SIGNATURE) }

    init {
        require(file.isFile) { "${file.name} is missing from ${file.parent}" }
        val start = SystemClock.elapsedRealtime()
        model = CompiledModel.create(file.absolutePath, config.options(), environment)
        compileMs = SystemClock.elapsedRealtime() - start
    }

    fun run(imageData: FloatArray): FloatArray {
        input.writeFloat(imageData)
        model.run(mapOf("args_0" to input), mapOf("output_0" to output), SIGNATURE)
        return output.readFloat()
    }

    override fun close() {
        input.close()
        output.close()
        model.close()
    }

    companion object {
        const val FILE = "yolo_detector_fp32.tflite"
        private const val SIGNATURE = "serving_default"
    }
}
