package com.manhwaviewer.data.ocr.eval.litert

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import com.manhwaviewer.data.ocr.hayai.HayaiGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiHost
import com.manhwaviewer.data.ocr.hayai.HayaiKvCache
import com.manhwaviewer.data.ocr.hayai.HayaiStepResult
import com.manhwaviewer.data.ocr.hayai.HayaiVisualFeatures
import com.manhwaviewer.data.ocr.inference.OrtMemoryOptions
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Hayai with the encoder on the LiteRT GPU and prefill/decode on ONNX Runtime's CPU, as the app runs
 * them today.
 *
 * The encoder is the heaviest graph on the CPU and the one the GPU runs 2.5–3x faster; the decode
 * steps are the opposite (the GPU spends them on kernel dispatch). Keeping prefill and decode on the
 * app's int8 ONNX graphs also keeps their shared weight blob, so only the encoder file changes.
 *
 * The encoder has one set of buffers and is driven from one thread, so concurrent recognitions queue
 * for it while their prefill and decode run in parallel on ORT, whose sessions are safe to run
 * concurrently. The ORT side is opened exactly as `HayaiOnnxGraphs.initialize()` opens it.
 */
class HybridHayaiGraphs(
    encoderFile: File,
    onnxDir: File,
    config: LiteRtConfig,
    intraOpThreads: Int = 4,
    memory: OrtMemoryOptions = OrtMemoryOptions.NO_ARENA,
) : HayaiGraphs {

    private val gpu = Executors.newSingleThreadExecutor()
    private lateinit var environment: Environment
    private lateinit var encoder: CompiledModel
    private lateinit var encoderInputs: Map<String, TensorBuffer>
    private lateinit var encoderOutputs: Map<String, TensorBuffer>

    /** Wall time of the GPU compile, for the cold-start figure. */
    val encoderCompileMs: Long

    private val env: OrtEnvironment = memory.environment(intraOpThreads)
    private val runOptions: OrtSession.RunOptions? = memory.newRunOptions()
    private val prefillSession: OrtSession
    private val decodeSession: OrtSession

    init {
        require(encoderFile.isFile) { "${encoderFile.name} is missing from ${encoderFile.parent}" }
        encoderCompileMs = gpu.submit(Callable {
            val start = System.nanoTime()
            environment = Environment.create()
            encoder = CompiledModel.create(encoderFile.absolutePath, config.options(), environment)
            encoderInputs = (0..2).associate { "args_$it" to encoder.createInputBuffer("args_$it", SIGNATURE) }
            encoderOutputs = mapOf("output_0" to encoder.createOutputBuffer("output_0", SIGNATURE))
            (System.nanoTime() - start) / 1_000_000
        }).get()
        fun open(name: String): OrtSession {
            val file = File(onnxDir, name)
            require(file.isFile) { "$name is missing from ${onnxDir.absolutePath}" }
            return OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                memory.configure(options, intraOpThreads)
                env.createSession(file.absolutePath, options)
            }
        }
        prefillSession = open("hayai_prefill.onnx")
        decodeSession = open("hayai_decode.onnx")
    }

    private class Visual(val values: FloatArray) : HayaiVisualFeatures {
        override fun close() = Unit
    }

    /** Same lifetime rule as `HayaiOnnxGraphs`: a step's result stays open until the next step has consumed it. */
    private class Result(val result: OrtSession.Result) : HayaiKvCache {
        private var closed = false

        override fun close() {
            if (!closed) {
                closed = true
                result.close()
            }
        }

        fun tensor(name: String): OnnxTensor =
            result.get(name).orElseThrow { IllegalStateException("output '$name' is missing") } as OnnxTensor
    }

    override fun encode(pixelValues: FloatArray, attentionMask: IntArray, posEmbeds: FloatArray): HayaiVisualFeatures {
        val mask = FloatArray(attentionMask.size) { if (attentionMask[it] == 0) HayaiHost.NEG_INF else 0f }
        val features = gpu.submit(Callable {
            encoderInputs.getValue("args_0").writeFloat(pixelValues)
            encoderInputs.getValue("args_1").writeFloat(mask)
            encoderInputs.getValue("args_2").writeFloat(posEmbeds)
            encoder.run(encoderInputs, encoderOutputs, SIGNATURE)
            encoderOutputs.getValue("output_0").readFloat()
        }).get()
        return Visual(features)
    }

    override fun prefill(visual: HayaiVisualFeatures, tokenId: Int, cos: FloatArray, sin: FloatArray, mask: FloatArray): HayaiStepResult {
        val total = HayaiHost.TOTAL.toLong()
        val feeds = mapOf(
            "visual_features" to floats((visual as Visual).values, 1, HayaiHost.MAX_PATCHES.toLong(), 768),
            "text_token_ids" to longs(tokenId, 1, 1),
            "cos" to floats(cos, 1, total, HayaiHost.D_AXIS.toLong()),
            "sin" to floats(sin, 1, total, HayaiHost.D_AXIS.toLong()),
            "mask" to floats(mask, 1, 1, total, total),
        )
        val result = try {
            run(prefillSession, feeds)
        } finally {
            feeds.values.forEach(OnnxTensor::close)
        }
        val step = Result(result)
        return HayaiStepResult(logitsOf(step), step)
    }

    override fun decodeStep(tokenId: Int, cos: FloatArray, sin: FloatArray, mask: FloatArray, pos: Int, cache: HayaiKvCache): HayaiStepResult {
        val previous = cache as Result
        val total = HayaiHost.TOTAL.toLong()
        val axis = HayaiHost.D_AXIS.toLong()
        val owned = mapOf(
            "token_ids" to longs(tokenId, 1, 1),
            "cos" to floats(cos, 1, 1, axis),
            "sin" to floats(sin, 1, 1, axis),
            "mask" to floats(mask, 1, 1, 1, total),
            "write_mask" to floats(HayaiHost.writeMask(pos), 1, 1, total, 1),
        )
        val feeds = HashMap<String, OnnxTensor>(owned)
        for (layer in 0 until LAYERS) {
            feeds["past_k_$layer"] = previous.tensor("new_k_$layer")
            feeds["past_v_$layer"] = previous.tensor("new_v_$layer")
        }
        val result = try {
            run(decodeSession, feeds)
        } finally {
            owned.values.forEach(OnnxTensor::close)
        }
        previous.close()
        val step = Result(result)
        return HayaiStepResult(logitsOf(step), step)
    }

    override fun close() {
        prefillSession.close()
        decodeSession.close()
        runOptions?.close()
        gpu.submit(Callable {
            (encoderInputs.values + encoderOutputs.values).forEach(TensorBuffer::close)
            encoder.close()
            environment.close()
        }).get()
        gpu.shutdown()
    }

    private fun run(session: OrtSession, feeds: Map<String, OnnxTensor>): OrtSession.Result =
        if (runOptions == null) session.run(feeds) else session.run(feeds, runOptions)

    private fun logitsOf(step: Result): FloatArray {
        val buffer = step.tensor("logits").floatBuffer
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    private fun floats(values: FloatArray, vararg shape: Long): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)

    private fun longs(value: Int, vararg shape: Long): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(value.toLong())), shape)

    private companion object {
        const val SIGNATURE = "serving_default"
        const val LAYERS = 12
    }
}
