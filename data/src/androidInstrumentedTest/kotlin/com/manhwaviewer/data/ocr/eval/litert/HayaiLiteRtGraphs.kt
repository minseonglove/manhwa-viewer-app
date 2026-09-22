package com.manhwaviewer.data.ocr.eval.litert

import android.os.SystemClock
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import com.manhwaviewer.data.ocr.hayai.HayaiGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiHost
import com.manhwaviewer.data.ocr.hayai.HayaiKvCache
import com.manhwaviewer.data.ocr.hayai.HayaiStepResult
import com.manhwaviewer.data.ocr.hayai.HayaiVisualFeatures
import java.io.File
import kotlin.math.abs
import org.json.JSONObject

/**
 * [HayaiGraphs] on LiteRT's CompiledModel, to measure the GPU path against ORT on the CPU.
 *
 * [layout] picks which export is loaded; the two differ in files and in three inputs:
 * - [Layout.AUTHOR]: the author's int8 export (`hayai_*_int8.tflite`). The GPU mis-computes its
 *   encoder and cannot take its decoder graphs, so on a GPU only the CPU fallback is meaningful.
 * - [Layout.GPU]: the GPU re-export (`hayai_gpu_*_f16.tflite`) — 4-D RoPE and GQA, fp16 weights
 *   without int8 DEQUANTIZE, the encoder mask as an additive float input, int32 token ids, and the
 *   decode cache slot as the one-hot [HayaiHost.writeMask] instead of an int64 `pos`.
 *
 * Tensors are addressed by signature name, never by position: the signature lists
 * them lexicographically (`args_0`, `args_1`, `args_10`, …), so the n-th buffer LiteRT hands back
 * is not the n-th argument of the exported module.
 *
 * Every buffer is allocated once and reused, so an instance serves one recognition at a time.
 * Handles carry the step that produced them and are checked when used — a second caller
 * interleaving with the first fails instead of silently reading the other's cache.
 *
 * [kvTransfer] decides how one step's cache becomes the next step's input:
 * - [KvTransfer.COPY] reads it back to the host and writes it into the input buffers, 3.9 MB each
 *   way per step.
 * - [KvTransfer.SWAP] alternates two buffer sets inside the decode model, handing a step's output
 *   buffers to the next step as its inputs. That requires the runtime to accept a buffer created
 *   for an output in an input slot; [probeSwap] checks it on the device before anything relies on it.
 *
 * Prefill's cache always crosses into decode by copy — once per recognition, and it avoids
 * relying on buffers being shareable between two compiled models.
 */
class HayaiLiteRtGraphs(
    modelDir: File,
    environment: Environment,
    private val layout: Layout,
    encoderConfig: LiteRtConfig,
    /** Prefill and decode: on this device their GPU compile fails or crashes, so they may need the CPU. */
    decoderConfig: LiteRtConfig = encoderConfig,
    /** Safe to change between recognitions: prefill always restarts from set 0. */
    var kvTransfer: KvTransfer = KvTransfer.COPY,
) : HayaiGraphs {

    enum class KvTransfer { COPY, SWAP }

    enum class Layout(val encoder: String, val prefill: String, val decode: String) {
        AUTHOR("hayai_encoder_int8.tflite", "hayai_decoder_prefill_int8.tflite", "hayai_decoder_decode_int8.tflite"),
        GPU("hayai_gpu_encoder_f16.tflite", "hayai_gpu_prefill_f16.tflite", "hayai_gpu_decode_f16.tflite"),
    }

    /** Wall time of each model's compile, for the cold-start figure reported next to the steady state. */
    val compileMs = LinkedHashMap<String, Long>()

    private val encoder = compile(environment, encoderConfig, File(modelDir, layout.encoder), "encoder")
    private val prefill = compile(environment, decoderConfig, File(modelDir, layout.prefill), "prefill")
    private val decode = compile(environment, decoderConfig, File(modelDir, layout.decode), "decode")

    private val encIn = inputs(encoder, 3)
    private val encOut = outputs(encoder, 1)
    private val preIn = inputs(prefill, 5)
    private val preOut = outputs(prefill, 1 + CACHES)
    private val decIn = inputs(decode, 5 + CACHES)
    private val decOut = outputs(decode, 1 + CACHES)

    /** Cache slots of the decode model: set 0 is its input buffers, set 1 its output buffers. */
    private val cacheSets = arrayOf(
        List(CACHES) { decIn.getValue(arg(5 + it)) },
        List(CACHES) { decOut.getValue(out(1 + it)) },
    )
    private var current = 0
    private var step = 0L

    private inner class Visual(val step: Long) : HayaiVisualFeatures {
        override fun close() = Unit
    }

    private inner class Cache(val step: Long) : HayaiKvCache {
        override fun close() = Unit
    }

    override fun encode(
        pixelValues: FloatArray,
        attentionMask: IntArray,
        posEmbeds: FloatArray,
    ): HayaiVisualFeatures {
        encIn.getValue(arg(0)).writeFloat(pixelValues)
        when (layout) {
            Layout.AUTHOR -> encIn.getValue(arg(1)).writeLong(LongArray(attentionMask.size) { attentionMask[it].toLong() })
            Layout.GPU -> encIn.getValue(arg(1)).writeFloat(
                FloatArray(attentionMask.size) { if (attentionMask[it] == 0) HayaiHost.NEG_INF else 0f },
            )
        }
        encIn.getValue(arg(2)).writeFloat(posEmbeds)
        encoder.run(encIn, encOut, SIGNATURE)
        return Visual(++step)
    }

    override fun prefill(
        visual: HayaiVisualFeatures,
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
    ): HayaiStepResult {
        check((visual as Visual).step == step) { "stale visual features: another recognition ran in between" }
        preIn.getValue(arg(0)).writeFloat(encOut.getValue(out(0)).readFloat())
        writeToken(preIn.getValue(arg(1)), tokenId)
        preIn.getValue(arg(2)).writeFloat(cos)
        preIn.getValue(arg(3)).writeFloat(sin)
        preIn.getValue(arg(4)).writeFloat(mask)
        prefill.run(preIn, preOut, SIGNATURE)
        current = 0
        for (i in 0 until CACHES) {
            cacheSets[current][i].writeFloat(preOut.getValue(out(1 + i)).readFloat())
        }
        return HayaiStepResult(preOut.getValue(out(0)).readFloat(), Cache(++step))
    }

    override fun decodeStep(
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
        pos: Int,
        cache: HayaiKvCache,
    ): HayaiStepResult {
        check((cache as Cache).step == step) { "stale cache: another recognition ran in between" }
        val next = 1 - current
        val feeds = HashMap<String, TensorBuffer>(5 + CACHES)
        feeds[arg(0)] = decIn.getValue(arg(0)).also { writeToken(it, tokenId) }
        feeds[arg(1)] = decIn.getValue(arg(1)).apply { writeFloat(cos) }
        feeds[arg(2)] = decIn.getValue(arg(2)).apply { writeFloat(sin) }
        feeds[arg(3)] = decIn.getValue(arg(3)).apply { writeFloat(mask) }
        feeds[arg(4)] = decIn.getValue(arg(4)).also { writeSlot(it, pos) }
        val fetches = HashMap<String, TensorBuffer>(1 + CACHES)
        fetches[out(0)] = decOut.getValue(out(0))
        for (i in 0 until CACHES) {
            feeds[arg(5 + i)] = cacheSets[current][i]
            fetches[out(1 + i)] = cacheSets[next][i]
        }
        decode.run(feeds, fetches, SIGNATURE)
        when (kvTransfer) {
            KvTransfer.SWAP -> current = next
            KvTransfer.COPY -> for (i in 0 until CACHES) {
                cacheSets[current][i].writeFloat(cacheSets[next][i].readFloat())
            }
        }
        return HayaiStepResult(decOut.getValue(out(0)).readFloat(), Cache(++step))
    }

    /**
     * Runs one decode step with SWAP's buffer arrangement and reports whether the runtime accepted
     * it and produced the same cache as COPY would. Call before any recognition.
     */
    fun probeSwap(): JSONObject {
        val result = JSONObject()
        val inputReq = decode.getInputBufferRequirements(arg(5), SIGNATURE)
        val outputReq = decode.getOutputBufferRequirements(out(1), SIGNATURE)
        result.put("cache_input_types", inputReq.supportedTypes.joinToString(",") { it.name })
        result.put("cache_output_types", outputReq.supportedTypes.joinToString(",") { it.name })
        result.put("cache_input_bytes", inputReq.bufferSize)
        result.put("cache_output_bytes", outputReq.bufferSize)
        val feeds = HashMap<String, TensorBuffer>()
        for (i in 0 until 5) feeds[arg(i)] = decIn.getValue(arg(i))
        writeToken(feeds.getValue(arg(0)), 1)
        feeds.getValue(arg(1)).writeFloat(FloatArray(32))
        feeds.getValue(arg(2)).writeFloat(FloatArray(32))
        feeds.getValue(arg(3)).writeFloat(FloatArray(320))
        writeSlot(feeds.getValue(arg(4)), 256)
        val seed = FloatArray(CACHE_FLOATS) { (it % 97) / 97f }
        cacheSets[1].forEach { it.writeFloat(seed) }
        val fetches = HashMap<String, TensorBuffer>()
        fetches[out(0)] = decOut.getValue(out(0))
        for (i in 0 until CACHES) {
            feeds[arg(5 + i)] = cacheSets[1][i]
            fetches[out(1 + i)] = cacheSets[0][i]
        }
        return try {
            decode.run(feeds, fetches, SIGNATURE)
            val swapped = cacheSets[0][0].readFloat()
            // Reference: the same step with the natural arrangement.
            cacheSets[0].forEach { it.writeFloat(seed) }
            for (i in 0 until CACHES) {
                feeds[arg(5 + i)] = cacheSets[0][i]
                fetches[out(1 + i)] = cacheSets[1][i]
            }
            decode.run(feeds, fetches, SIGNATURE)
            val natural = cacheSets[1][0].readFloat()
            var maxDiff = 0f
            for (j in natural.indices) maxDiff = maxOf(maxDiff, abs(natural[j] - swapped[j]))
            result.put("swap_runs", true).put("swap_max_abs_diff", maxDiff.toDouble())
        } catch (e: Exception) {
            result.put("swap_runs", false).put("swap_error", e.toString())
        }
    }

    override fun close() {
        (encIn.values + encOut.values + preIn.values + preOut.values + decIn.values + decOut.values)
            .forEach(TensorBuffer::close)
        encoder.close()
        prefill.close()
        decode.close()
    }

    private fun writeToken(buffer: TensorBuffer, tokenId: Int) = when (layout) {
        Layout.AUTHOR -> buffer.writeLong(longArrayOf(tokenId.toLong()))
        Layout.GPU -> buffer.writeInt(intArrayOf(tokenId))
    }

    private fun writeSlot(buffer: TensorBuffer, pos: Int) = when (layout) {
        Layout.AUTHOR -> buffer.writeLong(longArrayOf(pos.toLong()))
        Layout.GPU -> buffer.writeFloat(HayaiHost.writeMask(pos))
    }

    private fun compile(environment: Environment, config: LiteRtConfig, file: File, name: String): CompiledModel {
        require(file.isFile) { "${file.name} is missing from ${file.parent}" }
        val start = SystemClock.elapsedRealtime()
        return CompiledModel.create(file.absolutePath, config.options(), environment).also {
            compileMs[name] = SystemClock.elapsedRealtime() - start
        }
    }

    private fun inputs(model: CompiledModel, count: Int): Map<String, TensorBuffer> =
        (0 until count).associate { arg(it) to model.createInputBuffer(arg(it), SIGNATURE) }

    private fun outputs(model: CompiledModel, count: Int): Map<String, TensorBuffer> =
        (0 until count).associate { out(it) to model.createOutputBuffer(out(it), SIGNATURE) }

    private fun arg(i: Int) = "args_$i"

    private fun out(i: Int) = "output_$i"

    companion object {
        private const val SIGNATURE = "serving_default"
        private const val CACHES = 24
        private const val CACHE_FLOATS = 2 * 320 * 64
    }
}
