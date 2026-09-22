package com.manhwaviewer.data.ocr.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.image.NormRange
import com.manhwaviewer.data.ocr.image.resizeAndPadToFloatArray
import kotlin.math.abs

/**
 * The YOLO26n end2end detector on LiteRT: on the GPU when it compiles there and finds the same
 * text boxes as the CPU on a drawn check page, otherwise on the CPU.
 *
 * NCHW (1, 3, 640, 640) in, (1, 300, 6) out. On a Galaxy Z Flip7 the GPU runs the network in
 * ~25–40 ms.
 */
actual class YoloRunner(
    private val context: Context,
    private val preferGpu: Boolean = true,
) {
    private var model: CompiledModel? = null
    private var input: TensorBuffer? = null
    private var output: TensorBuffer? = null

    var accelerator: OcrAccelerator? = null
        private set

    /** Idempotent: concurrent first detections can each call it, and a second compile would leak the first model. */
    actual fun initialize() = LiteRtThread.call {
        if (model != null) return@call
        val opened = LiteRtModels.open(context, PlatformModelPaths.YOLO_MODEL_PATH, CACHE_NAME, preferGpu, ::agreesWithCpu)
        model = opened.model
        accelerator = opened.accelerator
        input = opened.model.createInputBuffer(INPUT, SIGNATURE)
        output = opened.model.createOutputBuffer(OUTPUT, SIGNATURE)
    }

    actual fun run(imageData: FloatArray): FloatArray = LiteRtThread.call {
        val model = checkNotNull(model) { "initialize() has not run" }
        val input = input!!
        val output = output!!
        input.writeFloat(imageData)
        model.run(mapOf(INPUT to input), mapOf(OUTPUT to output), SIGNATURE)
        output.readFloat()
    }

    actual fun close() = LiteRtThread.call {
        input?.close()
        output?.close()
        model?.close()
        input = null
        output = null
        model = null
    }

    private fun agreesWithCpu(gpu: CompiledModel): Boolean {
        val probe = checkPage()
        val cpu = LiteRtModels.compileOnCpu(context, PlatformModelPaths.YOLO_MODEL_PATH)
        return try {
            val onGpu = runOnce(gpu, probe)
            val onCpu = runOnce(cpu, probe)
            val gpuBoxes = boxes(onGpu)
            val cpuBoxes = boxes(onCpu)
            val boxesAgree = GpuSelfCheck.boxesAgree(gpuBoxes, cpuBoxes)
            val confidenceGap = topConfidenceGap(onGpu, onCpu)
            println(
                "OCR | YOLO GPU self-check: boxes gpu=${gpuBoxes.map { "%.2f".format(it.confidence) }} " +
                    "cpu=${cpuBoxes.map { "%.2f".format(it.confidence) }} agree=$boxesAgree, " +
                    "top confidence gap $confidenceGap (limit $MAX_CONFIDENCE_GAP)",
            )
            boxesAgree && confidenceGap <= MAX_CONFIDENCE_GAP
        } finally {
            cpu.close()
        }
    }

    private fun runOnce(model: CompiledModel, image: FloatArray): FloatArray {
        val input = model.createInputBuffer(INPUT, SIGNATURE)
        val output = model.createOutputBuffer(OUTPUT, SIGNATURE)
        return try {
            input.writeFloat(image)
            model.run(mapOf(INPUT to input), mapOf(OUTPUT to output), SIGNATURE)
            output.readFloat()
        } finally {
            input.close()
            output.close()
        }
    }

    /** Only confident boxes: a borderline one can flip between fp16 and fp32 without anything being wrong. */
    private fun boxes(raw: FloatArray) = YoloPostProcessor.nonMaxSuppression(
        YoloPostProcessor.parseAndFilter(raw, 1f, 0, 0, SIZE, SIZE, confThreshold = CHECK_CONFIDENCE),
    )

    /**
     * Largest difference between the ten strongest scores of each output, so a GPU that outputs
     * garbage cannot pass by finding nothing. Infinite if a score is not finite. Only a coarse
     * guard: on 30 real pages a working GPU (fp16) sat up to 0.155 from the CPU here.
     */
    private fun topConfidenceGap(a: FloatArray, b: FloatArray): Float {
        fun top(raw: FloatArray) = (0 until MangaOcrConfig.YOLO_MAX_DETECTIONS).map { raw[it * 6 + 4] }.sortedDescending().take(10)
        return top(a).zip(top(b)).maxOf { (x, y) -> if (x.isFinite() && y.isFinite()) abs(x - y) else Float.POSITIVE_INFINITY }
    }

    /** A page with two vertical speech balloons, drawn rather than shipped, through the app's own preprocessing. */
    private fun checkPage(): FloatArray {
        val page = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.rgb(200, 200, 200))
        val canvas = Canvas(page)
        val balloon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 34f }
        for ((left, lines) in listOf(90f to listOf("そのむかし", "とりがおった"), 380f to listOf("ある日", "ねながら"))) {
            canvas.drawOval(RectF(left, 90f, left + 170f, 560f), balloon)
            lines.forEachIndexed { column, line ->
                val x = left + 100f - column * 44f
                line.forEachIndexed { row, ch -> canvas.drawText(ch.toString(), x, 170f + row * 40f, text) }
            }
        }
        return try {
            resizeAndPadToFloatArray(page, SIZE, NormRange.ZERO_ONE, padColor = 0.5f)
        } finally {
            page.recycle()
        }
    }

    private companion object {
        const val SIGNATURE = "serving_default"
        const val INPUT = "args_0"
        const val OUTPUT = "output_0"
        const val SIZE = MangaOcrConfig.YOLO_INPUT_SIZE
        const val CHECK_CONFIDENCE = 0.3f
        const val MAX_CONFIDENCE_GAP = 0.3f
        const val CACHE_NAME = "yolo"
    }
}
