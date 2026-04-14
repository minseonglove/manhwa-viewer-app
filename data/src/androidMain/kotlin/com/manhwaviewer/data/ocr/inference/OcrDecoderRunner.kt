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
    private var crossAttnInitSession: OrtSession? = null
    private var decoderWithPastSession: OrtSession? = null

    actual fun initialize() {
        ortEnv = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        crossAttnInitSession = ortEnv!!.createSession(extractModel(PlatformModelPaths.CROSS_ATTN_INIT_PATH), options)
        decoderWithPastSession = ortEnv!!.createSession(extractModel(PlatformModelPaths.DECODER_WITH_PAST_PATH), options)
    }

    private var cachedEncoderFp16: ByteBuffer? = null
    private var cachedEncoderHash: Int = 0

    actual fun run(inputIds: IntArray, seqLen: Int, encoderOutput: FloatArray): FloatArray {
        error("Legacy decoder removed. Use beginSequence() + stepWithKvCache() instead.")
    }

    // ---- KV Cache methods ----

    actual fun beginSequence(encoderOutput: FloatArray): KvCacheState {
        val env = ortEnv!!
        val encoderFp16 = getOrCacheEncoderFp16(encoderOutput)

        // Run cross_attn_init to compute cross-attention KV
        val encoderShape = longArrayOf(1, MangaOcrConfig.ENCODER_SEQ_LENGTH.toLong(), MangaOcrConfig.ENCODER_HIDDEN_SIZE.toLong())
        val encoderTensor = OnnxTensor.createTensor(env, encoderFp16.rewound(), encoderShape, OnnxJavaType.FLOAT16)

        val initResults = crossAttnInitSession!!.run(mapOf("encoder_hidden_states" to encoderTensor))

        val crossKeys = Array(MangaOcrConfig.DECODER_NUM_LAYERS) { layer ->
            extractFp16Buffer(initResults.get(layer * 2) as OnnxTensor)
        }
        val crossValues = Array(MangaOcrConfig.DECODER_NUM_LAYERS) { layer ->
            extractFp16Buffer(initResults.get(layer * 2 + 1) as OnnxTensor)
        }

        initResults.close()
        encoderTensor.close()

        // Self-attention KV starts empty (seq_len=0)
        val emptySelfKeys = Array(MangaOcrConfig.DECODER_NUM_LAYERS) {
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }
        val emptySelfValues = Array(MangaOcrConfig.DECODER_NUM_LAYERS) {
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }

        return KvCacheState(encoderFp16, emptySelfKeys, emptySelfValues, crossKeys, crossValues)
    }

    actual fun beginSequence(encoderOutput: EncoderOutput): KvCacheState {
        val env = ortEnv!!
        val encoderFp16 = encoderOutput.fp16Buffer

        val encoderShape = longArrayOf(1, MangaOcrConfig.ENCODER_SEQ_LENGTH.toLong(), MangaOcrConfig.ENCODER_HIDDEN_SIZE.toLong())
        val encoderTensor = OnnxTensor.createTensor(env, encoderFp16.rewound(), encoderShape, OnnxJavaType.FLOAT16)

        val initResults = crossAttnInitSession!!.run(mapOf("encoder_hidden_states" to encoderTensor))

        val crossKeys = Array(MangaOcrConfig.DECODER_NUM_LAYERS) { layer ->
            extractFp16Buffer(initResults.get(layer * 2) as OnnxTensor)
        }
        val crossValues = Array(MangaOcrConfig.DECODER_NUM_LAYERS) { layer ->
            extractFp16Buffer(initResults.get(layer * 2 + 1) as OnnxTensor)
        }

        initResults.close()
        encoderTensor.close()

        val emptySelfKeys = Array(MangaOcrConfig.DECODER_NUM_LAYERS) {
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }
        val emptySelfValues = Array(MangaOcrConfig.DECODER_NUM_LAYERS) {
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        }

        return KvCacheState(encoderFp16, emptySelfKeys, emptySelfValues, crossKeys, crossValues)
    }

    actual fun stepWithKvCache(tokenId: Int, kvCache: KvCacheState): DecoderStepResult {
        val env = ortEnv!!
        val numLayers = MangaOcrConfig.DECODER_NUM_LAYERS
        val numHeads = MangaOcrConfig.DECODER_NUM_HEADS
        val headDim = MangaOcrConfig.DECODER_HEAD_DIM
        val encSeq = MangaOcrConfig.ENCODER_SEQ_LENGTH

        val feeds = mutableMapOf<String, OnnxTensor>()

        // input_ids: [1, 1]
        feeds["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(tokenId.toLong())), longArrayOf(1, 1))

        // encoder_hidden_states: [1, 197, 192] fp16 (cached)
        val encShape = longArrayOf(1, encSeq.toLong(), MangaOcrConfig.ENCODER_HIDDEN_SIZE.toLong())
        feeds["encoder_hidden_states"] = OnnxTensor.createTensor(env, kvCache.encoderFp16.rewound(), encShape, OnnxJavaType.FLOAT16)

        // Past KV cache tensors
        for (layer in 0 until numLayers) {
            val selfKeyBuf = kvCache.selfKeys[layer]
            val selfSeq = (selfKeyBuf.remaining() / (numHeads * headDim * 2)).toLong()

            feeds["past_self_key_layer$layer"] = OnnxTensor.createTensor(
                env, selfKeyBuf.rewound(),
                longArrayOf(1, numHeads.toLong(), selfSeq, headDim.toLong()),
                OnnxJavaType.FLOAT16,
            )
            feeds["past_self_value_layer$layer"] = OnnxTensor.createTensor(
                env, kvCache.selfValues[layer].rewound(),
                longArrayOf(1, numHeads.toLong(), selfSeq, headDim.toLong()),
                OnnxJavaType.FLOAT16,
            )
            feeds["past_cross_key_layer$layer"] = OnnxTensor.createTensor(
                env, kvCache.crossKeys[layer].rewound(),
                longArrayOf(1, numHeads.toLong(), encSeq.toLong(), headDim.toLong()),
                OnnxJavaType.FLOAT16,
            )
            feeds["past_cross_value_layer$layer"] = OnnxTensor.createTensor(
                env, kvCache.crossValues[layer].rewound(),
                longArrayOf(1, numHeads.toLong(), encSeq.toLong(), headDim.toLong()),
                OnnxJavaType.FLOAT16,
            )
        }

        val results = decoderWithPastSession!!.run(feeds)

        // Extract logits (fp16 → fp32, last position only)
        val logitsTensor = results.get(0) as OnnxTensor
        val logitsShort = logitsTensor.shortBuffer
        val logits = FloatArray(MangaOcrConfig.VOCAB_SIZE) { i -> halfToFloat(logitsShort.get(i)) }

        // Extract updated self-attention KV (outputs 1-8)
        // Output order: logits, present_self_key_0, present_self_value_0, present_cross_key_0, present_cross_value_0, ...
        val newSelfKeys = Array(numLayers) { layer ->
            extractFp16Buffer(results.get(1 + layer * 4) as OnnxTensor)
        }
        val newSelfValues = Array(numLayers) { layer ->
            extractFp16Buffer(results.get(2 + layer * 4) as OnnxTensor)
        }

        // Close tensors
        for (tensor in feeds.values) tensor.close()
        results.close()

        val newKv = KvCacheState(kvCache.encoderFp16, newSelfKeys, newSelfValues, kvCache.crossKeys, kvCache.crossValues)
        return DecoderStepResult(logits, newKv)
    }

    actual fun close() {
        crossAttnInitSession?.close(); crossAttnInitSession = null
        decoderWithPastSession?.close(); decoderWithPastSession = null
        ortEnv = null
        cachedEncoderFp16 = null
    }

    // ---- Private helpers ----

    private fun getOrCacheEncoderFp16(encoderOutput: FloatArray): ByteBuffer {
        val hash = encoderOutput.contentHashCode()
        if (hash == cachedEncoderHash && cachedEncoderFp16 != null) {
            return cachedEncoderFp16!!
        }
        val buffer = ByteBuffer.allocateDirect(encoderOutput.size * 2).order(ByteOrder.nativeOrder())
        for (i in encoderOutput.indices) {
            buffer.putShort(i * 2, floatToHalf(encoderOutput[i]))
        }
        cachedEncoderFp16 = buffer
        cachedEncoderHash = hash
        return buffer
    }

    private fun extractFp16Buffer(tensor: OnnxTensor): ByteBuffer {
        val shortBuffer = tensor.shortBuffer
        val byteBuffer = ByteBuffer.allocateDirect(shortBuffer.remaining() * 2)
            .order(ByteOrder.nativeOrder())
        byteBuffer.asShortBuffer().put(shortBuffer)
        return byteBuffer
    }

    private fun extractModel(assetPath: String): String {
        val file = File(context.cacheDir, assetPath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "${assetPath}.tmp")
            context.assets.open(assetPath).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(file)
        }
        return file.absolutePath
    }
}

/** Duplicate + rewind returning ByteBuffer (not Buffer). */
private fun ByteBuffer.rewound(): ByteBuffer = duplicate().apply { position(0) }
