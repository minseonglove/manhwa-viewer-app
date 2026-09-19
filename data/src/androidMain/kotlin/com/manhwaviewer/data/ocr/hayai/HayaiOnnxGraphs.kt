package com.manhwaviewer.data.ocr.hayai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * [HayaiGraphs] on ONNX Runtime, the inference runtime this app already ships.
 *
 * Measured against LiteRT on the same host: equal recognition quality, ~3x faster, and slightly
 * smaller once prefill and decode share their identical decoder weights through one external
 * data blob — so Hayai runs here rather than pulling a second inference library into the APK.
 *
 * [modelDir] holds `hayai_{encoder,prefill,decode}.onnx` and, when the weights are shared, the
 * blob they reference. ONNX resolves external data relative to the model file, so the blob has
 * to sit beside them — assets must be extracted to real files, not read from the APK.
 *
 * The decode graph takes a one-hot `write_mask` rather than a slot index, matching the exported
 * graph; [HayaiHost.writeMask] builds it.
 */
class HayaiOnnxGraphs(private val modelDir: File) : HayaiGraphs {

    /**
     * Outputs stay in the [OrtSession.Result] that produced them and are fed straight back in.
     *
     * The cache is 24 tensors of (1,2,320,64) — about 3.9 MB rewritten every step. Copying it
     * into Kotlin arrays and back would cost more than the graphs do. The price is a lifetime
     * rule: a result must stay open until the step that consumes it has run, so each step closes
     * the one it just consumed and never the one it is handing forward.
     */
    private class Result(val result: OrtSession.Result) : HayaiKvCache, HayaiVisualFeatures {
        fun tensor(name: String): OnnxTensor =
            result.get(name).orElseThrow {
                IllegalStateException("output '$name' is missing; the graph's IO does not match")
            } as OnnxTensor
    }

    private var env: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var prefillSession: OrtSession? = null
    private var decodeSession: OrtSession? = null

    /**
     * [xnnpackThreads] 가 0 이면 CPU 실행 공급자만 쓴다. 양수면 XNNPACK 을 그 스레드 수로 얹는다.
     * XNNPACK 은 감지된 CPU 기능(i8mm, sve2 등)에 따라 int8 마이크로커널을 고르므로, 어느 쪽이
     * 빠른지는 기기마다 다르고 실기기에서 재야 한다.
     *
     * [intraOpThreads] 가 0 이면 ORT 기본값(코어 수)을 쓴다. 기본값을 4 로 둔 것은 이 앱이
     * 영역 네 개를 동시에 인식하기 때문이다 — 추론 하나가 코어를 다 쥐면 넷이 같은 코어를
     * 나눠 쓰고 동기화 비용만 붙는다. 플립7(10코어)에서 페이지당 중앙값 기준 1스레드 1.037배,
     * 2스레드 0.981배, **4스레드 0.925배**, 6스레드 1.192배로 4가 가장 빨랐다. 6에서 급격히
     * 나빠지는 것은 big 클러스터를 넘어 little 코어까지 쓰게 되기 때문으로 보인다.
     * 기기마다 다를 수 있는 값이고, `YoloRunner` 도 같은 이유로 4를 쓴다.
     */
    fun initialize(xnnpackThreads: Int = 0, intraOpThreads: Int = DEFAULT_INTRA_OP_THREADS) {
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (intraOpThreads > 0) setIntraOpNumThreads(intraOpThreads)
            if (xnnpackThreads > 0) {
                addXnnpack(mapOf("intra_op_num_threads" to xnnpackThreads.toString()))
            }
        }
        env = environment
        encoderSession = open(environment, options, "hayai_encoder.onnx")
        prefillSession = open(environment, options, "hayai_prefill.onnx")
        decodeSession = open(environment, options, "hayai_decode.onnx")
    }

    private fun open(environment: OrtEnvironment, options: OrtSession.SessionOptions, name: String):
        OrtSession {
        val file = File(modelDir, name)
        require(file.isFile) { "$name is missing from ${modelDir.absolutePath}" }
        return environment.createSession(file.absolutePath, options)
    }

    override fun encode(
        pixelValues: FloatArray,
        attentionMask: IntArray,
        posEmbeds: FloatArray,
    ): HayaiVisualFeatures {
        val environment = env ?: error("initialize() has not run")
        val feeds = mapOf(
            "pixel_values" to floats(environment, pixelValues, 1, HayaiHost.MAX_PATCHES.toLong(), 768),
            "attention_mask" to longs(environment, attentionMask, 1, HayaiHost.MAX_PATCHES.toLong()),
            "pos_embeds" to floats(environment, posEmbeds, 1, HayaiHost.MAX_PATCHES.toLong(), 768),
        )
        return Result(runAndRelease(encoderSession ?: error("initialize() has not run"), feeds))
    }

    override fun prefill(
        visual: HayaiVisualFeatures,
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
    ): HayaiStepResult {
        val environment = env ?: error("initialize() has not run")
        val encoded = visual as Result
        val total = HayaiHost.TOTAL.toLong()
        val axis = HayaiHost.D_AXIS.toLong()
        val owned = mapOf(
            "text_token_ids" to longs(environment, intArrayOf(tokenId), 1, 1),
            "cos" to floats(environment, cos, 1, total, axis),
            "sin" to floats(environment, sin, 1, total, axis),
            "mask" to floats(environment, mask, 1, 1, total, total),
        )
        val feeds = owned + ("visual_features" to encoded.tensor("visual_features"))
        val result = try {
            (prefillSession ?: error("initialize() has not run")).run(feeds)
        } finally {
            owned.values.forEach(OnnxTensor::close)
        }
        encoded.result.close() // consumed; the encoder output is not needed again
        val step = Result(result)
        return HayaiStepResult(logitsOf(step), step)
    }

    override fun decodeStep(
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
        pos: Int,
        cache: HayaiKvCache,
    ): HayaiStepResult {
        val environment = env ?: error("initialize() has not run")
        val previous = cache as Result
        val total = HayaiHost.TOTAL.toLong()
        val axis = HayaiHost.D_AXIS.toLong()
        val owned = mapOf(
            "token_ids" to longs(environment, intArrayOf(tokenId), 1, 1),
            "cos" to floats(environment, cos, 1, 1, axis),
            "sin" to floats(environment, sin, 1, 1, axis),
            "mask" to floats(environment, mask, 1, 1, 1, total),
            "write_mask" to floats(environment, HayaiHost.writeMask(pos), 1, 1, total, 1),
        )
        val feeds = HashMap<String, OnnxTensor>(owned)
        for (layer in 0 until LAYERS) {
            feeds["past_k_$layer"] = previous.tensor("new_k_$layer")
            feeds["past_v_$layer"] = previous.tensor("new_v_$layer")
        }
        val result = try {
            (decodeSession ?: error("initialize() has not run")).run(feeds)
        } finally {
            owned.values.forEach(OnnxTensor::close)
        }
        previous.result.close() // consumed by the run above; safe only now
        val step = Result(result)
        return HayaiStepResult(logitsOf(step), step)
    }

    override fun close() {
        encoderSession?.close()
        prefillSession?.close()
        decodeSession?.close()
        encoderSession = null
        prefillSession = null
        decodeSession = null
        env = null
    }

    private fun runAndRelease(session: OrtSession, feeds: Map<String, OnnxTensor>): OrtSession.Result =
        try {
            session.run(feeds)
        } finally {
            feeds.values.forEach(OnnxTensor::close)
        }

    private fun logitsOf(step: Result): FloatArray {
        val buffer = step.tensor("logits").floatBuffer
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun floats(
        environment: OrtEnvironment,
        values: FloatArray,
        vararg shape: Long,
    ): OnnxTensor {
        val elements = shape.fold(1L) { acc, n -> acc * n }
        require(elements == values.size.toLong()) {
            "shape ${shape.toList()} holds $elements elements, got ${values.size}"
        }
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape)
    }

    /** The graphs declare int64 where the loop works in Int. */
    private fun longs(
        environment: OrtEnvironment,
        values: IntArray,
        vararg shape: Long,
    ): OnnxTensor {
        val elements = shape.fold(1L) { acc, n -> acc * n }
        require(elements == values.size.toLong()) {
            "shape ${shape.toList()} holds $elements elements, got ${values.size}"
        }
        val widened = LongArray(values.size) { values[it].toLong() }
        return OnnxTensor.createTensor(environment, LongBuffer.wrap(widened), shape)
    }

    private companion object {
        const val LAYERS = 12
        const val DEFAULT_INTRA_OP_THREADS = 4
    }
}
