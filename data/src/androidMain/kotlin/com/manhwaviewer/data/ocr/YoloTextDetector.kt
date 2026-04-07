package com.manhwaviewer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.FloatBuffer

class YoloTextDetector(
    private val context: Context,
) : TextDetector {

    companion object {
        private const val CONF_THRESHOLD = 0.3f
        private const val TEXT_CLASS_ID = 1f
        private const val PADDING_PX = 4
    }

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var initialized = false

    override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return emptyList()

        if (!initialized) {
            initialize()
            initialized = true
        }

        val (inputBuffer, transform) = preprocess(bitmap)

        val shape = longArrayOf(1, 3, MangaOcrConfig.YOLO_INPUT_SIZE.toLong(), MangaOcrConfig.YOLO_INPUT_SIZE.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape)

        val results = session!!.run(mapOf("images" to tensor))

        val outputTensor = results.get("output0").get() as OnnxTensor
        val outputData = outputTensor.floatBuffer
        val detections = postProcess(outputData, transform, bitmap.width, bitmap.height)

        results.close()
        tensor.close()

        val regions = detections.mapNotNull { box ->
            val x = (box.x - PADDING_PX).coerceAtLeast(0)
            val y = (box.y - PADDING_PX).coerceAtLeast(0)
            val w = (box.width + PADDING_PX * 2).coerceAtMost(bitmap.width - x)
            val h = (box.height + PADDING_PX * 2).coerceAtMost(bitmap.height - y)
            if (w <= 0 || h <= 0) return@mapNotNull null

            val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            if (cropped !== bitmap) cropped.recycle()

            DetectedRegion(
                x = x,
                y = y,
                width = w,
                height = h,
                croppedImageBytes = baos.toByteArray(),
            )
        }

        bitmap.recycle()
        return regions
    }

    private fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        session = ortEnv!!.createSession(getModelPath(), options)
    }

    private fun preprocess(bitmap: Bitmap): Pair<FloatBuffer, YoloTransform> {
        val scale = MangaOcrConfig.YOLO_INPUT_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val padX = (MangaOcrConfig.YOLO_INPUT_SIZE - scaledW) / 2
        val padY = (MangaOcrConfig.YOLO_INPUT_SIZE - scaledH) / 2

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val padded = Bitmap.createBitmap(MangaOcrConfig.YOLO_INPUT_SIZE, MangaOcrConfig.YOLO_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        padded.eraseColor(Color.GRAY)
        Canvas(padded).drawBitmap(scaled, padX.toFloat(), padY.toFloat(), null)
        if (scaled !== bitmap) scaled.recycle()

        val pixels = IntArray(MangaOcrConfig.YOLO_INPUT_SIZE * MangaOcrConfig.YOLO_INPUT_SIZE)
        padded.getPixels(pixels, 0, MangaOcrConfig.YOLO_INPUT_SIZE, 0, 0, MangaOcrConfig.YOLO_INPUT_SIZE, MangaOcrConfig.YOLO_INPUT_SIZE)
        padded.recycle()

        val area = MangaOcrConfig.YOLO_INPUT_SIZE * MangaOcrConfig.YOLO_INPUT_SIZE
        val buffer = FloatBuffer.allocate(3 * area)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.put(i, (pixel shr 16 and 0xFF) / 255.0f)
            buffer.put(area + i, (pixel shr 8 and 0xFF) / 255.0f)
            buffer.put(2 * area + i, (pixel and 0xFF) / 255.0f)
        }

        return buffer to YoloTransform(scale, padX, padY)
    }

    private fun postProcess(
        output: FloatBuffer,
        transform: YoloTransform,
        originalWidth: Int,
        originalHeight: Int,
    ): List<DetectedBox> {
        val boxes = mutableListOf<DetectedBox>()

        for (i in 0 until MangaOcrConfig.YOLO_MAX_DETECTIONS) {
            val offset = i * 6
            val x1 = output.get(offset)
            val y1 = output.get(offset + 1)
            val x2 = output.get(offset + 2)
            val y2 = output.get(offset + 3)
            val confidence = output.get(offset + 4)
            val classId = output.get(offset + 5)

            if (confidence < CONF_THRESHOLD) continue
            if (classId != TEXT_CLASS_ID) continue

            val bx = ((x1 - transform.padX) / transform.scale).toInt().coerceIn(0, originalWidth)
            val by = ((y1 - transform.padY) / transform.scale).toInt().coerceIn(0, originalHeight)
            val bx2 = ((x2 - transform.padX) / transform.scale).toInt().coerceIn(0, originalWidth)
            val by2 = ((y2 - transform.padY) / transform.scale).toInt().coerceIn(0, originalHeight)
            val bw = bx2 - bx
            val bh = by2 - by
            if (bw <= 0 || bh <= 0) continue

            boxes.add(DetectedBox(bx, by, bw, bh, confidence, isJapanese = true))
        }

        return boxes
    }

    private fun getModelPath(): String {
        val file = File(context.cacheDir, MangaOcrConfig.YOLO_MODEL)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "${MangaOcrConfig.YOLO_MODEL}.tmp")
            context.assets.open(MangaOcrConfig.YOLO_MODEL).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(file)
        }
        return file.absolutePath
    }
}

private data class YoloTransform(
    val scale: Float,
    val padX: Int,
    val padY: Int,
)
