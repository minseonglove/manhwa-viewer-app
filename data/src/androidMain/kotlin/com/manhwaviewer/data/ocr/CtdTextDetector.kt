package com.manhwaviewer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxJavaType
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CtdTextDetector(
    private val context: Context,
) : TextDetector {

    companion object {
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

        val targetSize = MangaOcrConfig.CTD_INPUT_SIZE
        val (inputBuffer, transform) = preprocessForCtd(bitmap, targetSize)

        val shape = longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        val tensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape, OnnxJavaType.FLOAT16)

        val results = session!!.run(mapOf("images" to tensor))

        val blkTensor = results.get("blk").get() as OnnxTensor
        val blkShortBuffer = blkTensor.shortBuffer
        val blkSize = MangaOcrConfig.CTD_NUM_ANCHORS * 7
        val blkFloat = FloatArray(blkSize)
        for (i in 0 until blkSize) {
            blkFloat[i] = halfToFloat(blkShortBuffer.get(i))
        }

        results.close()
        tensor.close()

        val boxes = CtdPostProcessor.processBlkOutput(
            blk = blkFloat,
            numAnchors = MangaOcrConfig.CTD_NUM_ANCHORS,
            transform = transform,
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
        )

        val regions = boxes.mapNotNull { box ->
            val x = (box.x - PADDING_PX).coerceAtLeast(0)
            val y = (box.y - PADDING_PX).coerceAtLeast(0)
            val w = (box.width + PADDING_PX * 2).coerceAtMost(bitmap.width - x)
            val h = (box.height + PADDING_PX * 2).coerceAtMost(bitmap.height - y)
            if (w <= 0 || h <= 0) return@mapNotNull null

            val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.PNG, 100, baos)
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
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }
        session = ortEnv!!.createSession(getModelPath(MangaOcrConfig.CTD_MODEL), options)
    }

    private fun preprocessForCtd(
        bitmap: Bitmap,
        targetSize: Int,
    ): Pair<ByteBuffer, CtdTransform> {
        val scale = targetSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val padX = (targetSize - scaledW) / 2
        val padY = (targetSize - scaledH) / 2

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val padded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        padded.eraseColor(Color.GRAY)
        Canvas(padded).drawBitmap(scaled, padX.toFloat(), padY.toFloat(), null)
        if (scaled !== bitmap) scaled.recycle()

        val pixels = IntArray(targetSize * targetSize)
        padded.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
        padded.recycle()

        val area = targetSize * targetSize
        val buffer = ByteBuffer.allocateDirect(3 * area * 2).order(ByteOrder.nativeOrder())
        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.putShort(i * 2, floatToHalf((pixel shr 16 and 0xFF) / 255.0f))
            buffer.putShort((area + i) * 2, floatToHalf((pixel shr 8 and 0xFF) / 255.0f))
            buffer.putShort((2 * area + i) * 2, floatToHalf((pixel and 0xFF) / 255.0f))
        }

        return buffer to CtdTransform(scale, padX, padY)
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
}
