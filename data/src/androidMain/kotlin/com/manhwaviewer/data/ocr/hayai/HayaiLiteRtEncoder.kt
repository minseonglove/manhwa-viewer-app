package com.manhwaviewer.data.ocr.hayai

import android.content.Context
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.manhwaviewer.data.ocr.inference.GpuSelfCheck
import com.manhwaviewer.data.ocr.inference.LiteRtModels
import com.manhwaviewer.data.ocr.inference.LiteRtThread
import com.manhwaviewer.data.ocr.inference.OcrAccelerator
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hayai's vision encoder on LiteRT, from a re-export the GPU delegate can take: the int8 weights
 * widened to fp16 constants (the delegate misreads weights that arrive through a DEQUANTIZE) and
 * the padding mask as an additive float input (it cannot take int64).
 *
 * Kept on the GPU only if it reproduces, for a fixed input, a reference output computed offline on
 * the CPU; otherwise the same file runs on the CPU.
 */
class HayaiLiteRtEncoder(
    context: Context,
    positionBase: FloatArray,
    preferGpu: Boolean = true,
) {
    private val model: CompiledModel
    private lateinit var inputs: Map<String, TensorBuffer>
    private lateinit var outputs: Map<String, TensorBuffer>

    val accelerator: OcrAccelerator

    init {
        val reference = readReference(context)
        val opened = LiteRtModels.open(context, ASSET, CACHE_NAME, preferGpu) { agreesWithReference(it, positionBase, reference) }
        model = opened.model
        accelerator = opened.accelerator
        LiteRtThread.call {
            inputs = (0..2).associate { "args_$it" to model.createInputBuffer("args_$it", SIGNATURE) }
            outputs = mapOf(OUTPUT to model.createOutputBuffer(OUTPUT, SIGNATURE))
        }
    }

    /** Flat `MAX_PATCHES * 768` visual features for the patches [attentionMask] marks as real. */
    fun encode(pixelValues: FloatArray, attentionMask: IntArray, posEmbeds: FloatArray): FloatArray {
        val mask = HayaiHost.additiveMask(attentionMask)
        return LiteRtThread.call { run(model, inputs, outputs, pixelValues, mask, posEmbeds) }
    }

    fun close() = LiteRtThread.call {
        (inputs.values + outputs.values).forEach(TensorBuffer::close)
        model.close()
    }

    private fun agreesWithReference(candidate: CompiledModel, positionBase: FloatArray, reference: FloatArray): Boolean {
        val ins = (0..2).associate { "args_$it" to candidate.createInputBuffer("args_$it", SIGNATURE) }
        val outs = mapOf(OUTPUT to candidate.createOutputBuffer(OUTPUT, SIGNATURE))
        return try {
            val features = run(
                candidate, ins, outs,
                checkPixels(),
                HayaiHost.additiveMask(HayaiHost.attentionMask(CHECK_ROWS, CHECK_COLS)),
                HayaiHost.positionEmbeddings(positionBase, 16, 16),
            )
            val sample = FloatArray(reference.size) { features[it * REFERENCE_STRIDE] }
            val error = GpuSelfCheck.relativeError(sample, reference)
            println("OCR | Hayai encoder GPU self-check: relative error $error (limit $MAX_RELATIVE_ERROR)")
            error <= MAX_RELATIVE_ERROR
        } finally {
            (ins.values + outs.values).forEach(TensorBuffer::close)
        }
    }

    private fun run(
        model: CompiledModel,
        inputs: Map<String, TensorBuffer>,
        outputs: Map<String, TensorBuffer>,
        pixelValues: FloatArray,
        mask: FloatArray,
        posEmbeds: FloatArray,
    ): FloatArray {
        inputs.getValue("args_0").writeFloat(pixelValues)
        inputs.getValue("args_1").writeFloat(mask)
        inputs.getValue("args_2").writeFloat(posEmbeds)
        model.run(inputs, outputs, SIGNATURE)
        return outputs.getValue(OUTPUT).readFloat()
    }

    private fun readReference(context: Context): FloatArray {
        val bytes = context.assets.open(REFERENCE_ASSET).use { it.readBytes() }
        return FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
    }

    companion object {
        const val ASSET = "hayai/hayai_encoder_litert_f16.tflite"

        /**
         * Every [REFERENCE_STRIDE]th output value for [checkPixels] with only the first
         * [CHECK_ROWS] x [CHECK_COLS] patches real, computed with LiteRT on the CPU.
         */
        const val REFERENCE_ASSET = "hayai/hayai_encoder_litert_check.f32"
        const val REFERENCE_STRIDE = 96

        /**
         * Half the patches are padding, as in most real crops, so the check runs the masked path:
         * the -1e9 mask is -inf in fp16. A GPU that ignored the mask would be off by ~68%.
         */
        const val CHECK_ROWS = 8
        const val CHECK_COLS = 16

        /** fp16 on the GPU lands within a few percent of the CPU; the encoder the GPU mis-computed was off by ~220%. */
        const val MAX_RELATIVE_ERROR = 0.1f

        const val CACHE_NAME = "hayai-encoder"

        private const val SIGNATURE = "serving_default"
        private const val OUTPUT = "output_0"

        /** The check input: deterministic values in [-1, 1], reproducible offline without an image. */
        fun checkPixels(): FloatArray = FloatArray(HayaiHost.MAX_PATCHES * 768) { ((it * 7919) % 511) / 255f - 1f }
    }
}
