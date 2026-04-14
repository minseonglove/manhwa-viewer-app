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
import java.nio.LongBuffer

actual class OcrDecoderRunner(private val context: Context) {
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    actual fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        session = ortEnv!!.createSession(getModelPath(), OrtSession.SessionOptions())
    }

    actual fun run(inputIds: IntArray, seqLen: Int, encoderOutput: FloatArray): FloatArray {
        val env = ortEnv!!
        // Encoder output float32 → float16
        val encoderBuffer = ByteBuffer.allocateDirect(encoderOutput.size * 2).order(ByteOrder.nativeOrder())
        for (i in encoderOutput.indices) { encoderBuffer.putShort(i * 2, floatToHalf(encoderOutput[i])) }
        val encoderShape = longArrayOf(1, MangaOcrConfig.ENCODER_SEQ_LENGTH.toLong(), MangaOcrConfig.ENCODER_HIDDEN_SIZE.toLong())
        val encoderTensor = OnnxTensor.createTensor(env, encoderBuffer, encoderShape, OnnxJavaType.FLOAT16)

        // Input IDs
        val longIds = LongArray(seqLen) { inputIds[it].toLong() }
        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longIds), longArrayOf(1, seqLen.toLong()))

        val results = session!!.run(mapOf("input_ids" to inputIdsTensor, "encoder_hidden_states" to encoderTensor))
        val logitsTensor = results.get(0) as OnnxTensor
        val logitsShort = logitsTensor.shortBuffer
        val lastPosOffset = (seqLen - 1) * MangaOcrConfig.VOCAB_SIZE
        val logits = FloatArray(MangaOcrConfig.VOCAB_SIZE) { i -> halfToFloat(logitsShort.get(lastPosOffset + i)) }

        results.close()
        inputIdsTensor.close()
        encoderTensor.close()
        return logits
    }

    actual fun close() {
        session?.close(); session = null; ortEnv = null
    }

    private fun getModelPath(): String {
        val file = File(context.cacheDir, PlatformModelPaths.DECODER_MODEL_PATH)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "${PlatformModelPaths.DECODER_MODEL_PATH}.tmp")
            context.assets.open(PlatformModelPaths.DECODER_MODEL_PATH).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(file)
        }
        return file.absolutePath
    }
}
