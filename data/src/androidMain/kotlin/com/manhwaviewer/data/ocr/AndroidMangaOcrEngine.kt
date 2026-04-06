package com.manhwaviewer.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer

class AndroidMangaOcrEngine(
    private val context: Context,
) : MangaOcrEngine {

    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private lateinit var tokenizer: MangaOcrTokenizer
    private var initialized = false

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        if (!initialized) {
            initialize()
            initialized = true
        }

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IllegalArgumentException("Failed to decode image bytes")
        val resized = resizeWithPadding(bitmap, MangaOcrConfig.IMAGE_SIZE)
        if (resized !== bitmap) bitmap.recycle()

        val pixelValues = preprocessImage(resized)
        resized.recycle()

        val encoderOutput = runEncoder(pixelValues)
        val tokenIds = greedyDecode(encoderOutput)
        return tokenizer.decode(tokenIds)
    }

    private fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()

        encoderSession = ortEnv!!.createSession(getModelPath(MangaOcrConfig.ENCODER_MODEL), options)
        decoderSession = ortEnv!!.createSession(getModelPath(MangaOcrConfig.DECODER_MODEL), options)

        val vocabLines = context.assets.open(MangaOcrConfig.VOCAB_FILE).bufferedReader().readLines()
        tokenizer = MangaOcrTokenizer(vocabLines)
    }

    private fun getModelPath(assetName: String): String {
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "$assetName.tmp")
            context.assets.open(assetName).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(file)
        }
        return file.absolutePath
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val size = MangaOcrConfig.IMAGE_SIZE
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val area = size * size
        val buffer = ByteBuffer.allocateDirect(3 * area * 2)
            .order(ByteOrder.nativeOrder())
        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.putShort(i * 2, floatToHalf(((pixel shr 16 and 0xFF) / 127.5f) - 1.0f))
            buffer.putShort((area + i) * 2, floatToHalf(((pixel shr 8 and 0xFF) / 127.5f) - 1.0f))
            buffer.putShort((2 * area + i) * 2, floatToHalf(((pixel and 0xFF) / 127.5f) - 1.0f))
        }
        return buffer
    }

    private fun runEncoder(pixelValues: ByteBuffer): ByteBuffer {
        val env = ortEnv!!
        val shape = longArrayOf(
            1, 3, MangaOcrConfig.IMAGE_SIZE.toLong(), MangaOcrConfig.IMAGE_SIZE.toLong()
        )
        val tensor = OnnxTensor.createTensor(env, pixelValues, shape, OnnxJavaType.FLOAT16)

        val results = encoderSession!!.run(mapOf("pixel_values" to tensor))
        val outputTensor = results.get(0) as OnnxTensor
        val shortBuffer = outputTensor.shortBuffer
        val output = ByteBuffer.allocateDirect(shortBuffer.remaining() * 2)
            .order(ByteOrder.nativeOrder())
        for (i in 0 until shortBuffer.remaining()) {
            output.putShort(i * 2, shortBuffer.get(i))
        }

        results.close()
        tensor.close()
        return output
    }

    private fun greedyDecode(encoderOutput: ByteBuffer): List<Int> {
        val env = ortEnv!!
        val encoderShape = longArrayOf(
            1,
            MangaOcrConfig.ENCODER_SEQ_LENGTH.toLong(),
            MangaOcrConfig.ENCODER_HIDDEN_SIZE.toLong(),
        )
        val encoderTensor = OnnxTensor.createTensor(env, encoderOutput, encoderShape, OnnxJavaType.FLOAT16)

        val inputIds = LongArray(MangaOcrConfig.MAX_LENGTH + 1)
        inputIds[0] = MangaOcrConfig.DECODER_START_TOKEN_ID.toLong()
        var seqLen = 1

        try {
            for (step in 0 until MangaOcrConfig.MAX_LENGTH) {
                val inputIdsTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(inputIds, 0, seqLen),
                    longArrayOf(1, seqLen.toLong()),
                )
                val results = decoderSession!!.run(
                    mapOf(
                        "input_ids" to inputIdsTensor,
                        "encoder_hidden_states" to encoderTensor,
                    )
                )

                val logitsTensor = results.get(0) as OnnxTensor
                val logitsShort = logitsTensor.shortBuffer
                val lastPosOffset = (seqLen - 1) * MangaOcrConfig.VOCAB_SIZE

                var maxIdx = 0
                var maxVal = halfToFloat(logitsShort.get(lastPosOffset))
                for (i in 1 until MangaOcrConfig.VOCAB_SIZE) {
                    val v = halfToFloat(logitsShort.get(lastPosOffset + i))
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = i
                    }
                }

                results.close()
                inputIdsTensor.close()

                if (maxIdx == MangaOcrConfig.EOS_TOKEN_ID) break
                inputIds[seqLen++] = maxIdx.toLong()
            }
        } finally {
            encoderTensor.close()
        }

        return (0 until seqLen).map { inputIds[it].toInt() }
    }

    private fun resizeWithPadding(bitmap: Bitmap, targetSize: Int): Bitmap {
        val scale = targetSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        val padded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        padded.eraseColor(Color.WHITE)
        val canvas = Canvas(padded)
        val left = (targetSize - scaledW) / 2f
        val top = (targetSize - scaledH) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        if (scaled !== bitmap) scaled.recycle()

        return padded
    }

    override fun close() {
        encoderSession?.close()
        decoderSession?.close()
        encoderSession = null
        decoderSession = null
        ortEnv = null
        initialized = false
    }
}
