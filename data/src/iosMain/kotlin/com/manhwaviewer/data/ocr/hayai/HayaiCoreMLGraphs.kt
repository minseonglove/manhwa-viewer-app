package com.manhwaviewer.data.ocr.hayai

import kotlinx.cinterop.*
import platform.CoreML.*
import platform.Foundation.NSBundle
import platform.Foundation.NSNumber
import platform.posix.memcpy

/**
 * [HayaiGraphs] on CoreML, for the three `.mlmodelc` bundles converted from the checkpoint.
 *
 * Two adaptations live here and nowhere else, which is the same split the reference kept:
 *  * the decode graph takes a one-hot `write_mask` where the slot index would otherwise have to
 *    be derived in-graph, so [decodeStep] expands `pos` via [HayaiHost.writeMask];
 *  * the graphs declare int32 where the loop works in Int, so token ids are written as int32.
 *
 * Features and caches are handed back to CoreML as the very objects it produced. Nothing about
 * the 3.9 MB KV cache is copied into Kotlin between steps.
 */
private const val HALF_SUBNORMAL_STEP = 5.9604645E-8f // 2^-24

class HayaiCoreMLGraphs(
    private val subdirectory: String = "hayai",
) : HayaiGraphs {

    /** CoreML 출력은 ARC 가 회수하므로 놓아줄 것이 따로 없다. 계약만 만족시킨다. */
    private class Features(val provider: MLFeatureProviderProtocol) : HayaiVisualFeatures {
        override fun close() = Unit
    }
    private class Cache(val provider: MLFeatureProviderProtocol) : HayaiKvCache {
        override fun close() = Unit
    }

    private var encoder: MLModel? = null
    private var prefillModel: MLModel? = null
    private var decodeModel: MLModel? = null

    fun initialize(computeUnits: MLComputeUnits = MLComputeUnitsCPUAndNeuralEngine) {
        encoder = load("hayai_encoder", computeUnits)
        prefillModel = load("hayai_prefill", computeUnits)
        decodeModel = load("hayai_decode", computeUnits)
    }

    private fun load(name: String, computeUnits: MLComputeUnits): MLModel {
        val url = NSBundle.mainBundle.URLForResource(name, "mlmodelc", subdirectory)
            ?: error("$name.mlmodelc not found in bundle subdirectory '$subdirectory'")
        val config = MLModelConfiguration().apply { setComputeUnits(computeUnits) }
        return MLModel.modelWithContentsOfURL(url, config, null)
            ?: error("failed to load $name.mlmodelc")
    }

    override fun encode(
        pixelValues: FloatArray,
        attentionMask: IntArray,
        posEmbeds: FloatArray,
    ): HayaiVisualFeatures {
        val result = predict(
            encoder ?: error("initialize() has not run"),
            mapOf(
                "pixel_values" to floats(pixelValues, listOf(1, HayaiHost.MAX_PATCHES, 768)),
                "attention_mask" to ints(attentionMask, listOf(1, HayaiHost.MAX_PATCHES)),
                "pos_embeds" to floats(posEmbeds, listOf(1, HayaiHost.MAX_PATCHES, 768)),
            ),
        )
        return Features(result)
    }

    override fun prefill(
        visual: HayaiVisualFeatures,
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
    ): HayaiStepResult {
        val features = (visual as Features).provider
        val result = predict(
            prefillModel ?: error("initialize() has not run"),
            mapOf(
                "visual_features" to multiArray(features, VISUAL_OUTPUT),
                "text_token_ids" to ints(intArrayOf(tokenId), listOf(1, 1)),
                "cos" to floats(cos, listOf(1, HayaiHost.TOTAL, HayaiHost.D_AXIS)),
                "sin" to floats(sin, listOf(1, HayaiHost.TOTAL, HayaiHost.D_AXIS)),
                "mask" to floats(mask, listOf(1, 1, HayaiHost.TOTAL, HayaiHost.TOTAL)),
            ),
        )
        return HayaiStepResult(logitsOf(result), Cache(result))
    }

    override fun decodeStep(
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
        pos: Int,
        cache: HayaiKvCache,
    ): HayaiStepResult {
        val previous = (cache as Cache).provider
        val inputs = mutableMapOf<Any?, Any?>(
            "token_ids" to ints(intArrayOf(tokenId), listOf(1, 1)),
            "cos" to floats(cos, listOf(1, 1, HayaiHost.D_AXIS)),
            "sin" to floats(sin, listOf(1, 1, HayaiHost.D_AXIS)),
            "mask" to floats(mask, listOf(1, 1, 1, HayaiHost.TOTAL)),
            "write_mask" to floats(HayaiHost.writeMask(pos), listOf(1, 1, HayaiHost.TOTAL, 1)),
        )
        for (layer in 0 until LAYERS) {
            inputs["past_k_$layer"] = multiArray(previous, "new_k_$layer")
            inputs["past_v_$layer"] = multiArray(previous, "new_v_$layer")
        }
        val result = predict(decodeModel ?: error("initialize() has not run"), inputs)
        return HayaiStepResult(logitsOf(result), Cache(result))
    }

    override fun close() {
        encoder = null
        prefillModel = null
        decodeModel = null
    }

    // --- CoreML plumbing ----------------------------------------------------------------

    private fun predict(model: MLModel, inputs: Map<Any?, Any?>): MLFeatureProviderProtocol {
        val provider = MLDictionaryFeatureProvider(inputs, null)
        return model.predictionFromFeatures(provider, null) ?: error("CoreML inference failed")
    }

    private fun multiArray(provider: MLFeatureProviderProtocol, name: String): MLMultiArray =
        provider.featureValueForName(name)?.multiArrayValue
            ?: error("output '$name' is missing; the graph's IO does not match what the loop assumes")

    /**
     * The Neural Engine hands outputs back as float16, often with padded strides, while the CPU
     * path returns contiguous float32. Reading either as the other yields plausible-looking garbage:
     * the loop never sees EOS and runs to its length limit.
     */
    private fun logitsOf(provider: MLFeatureProviderProtocol): FloatArray {
        val array = multiArray(provider, "logits")
        val count = array.count.toInt()
        val source = array.dataPointer ?: error("logits carries no data pointer")
        val shape = array.shape.map { (it as NSNumber).intValue }
        val strides = array.strides.map { (it as NSNumber).intValue }
        val vocabulary = shape.last()
        require(count == vocabulary) { "logits shape $shape holds more than one position" }
        val stride = strides.last()
        return when (array.dataType) {
            MLMultiArrayDataTypeFloat32 -> {
                val values = source.reinterpret<FloatVar>()
                FloatArray(vocabulary) { values[it * stride] }
            }
            MLMultiArrayDataTypeFloat16 -> {
                val values = source.reinterpret<UShortVar>()
                FloatArray(vocabulary) { halfToFloat(values[it * stride].toInt()) }
            }
            else -> error("logits come back as data type ${array.dataType}")
        }
    }

    private fun halfToFloat(bits: Int): Float {
        val sign = (bits shr 15) and 0x1
        val exponent = (bits shr 10) and 0x1F
        val mantissa = bits and 0x3FF
        val magnitude = when (exponent) {
            0 -> mantissa * HALF_SUBNORMAL_STEP
            0x1F -> if (mantissa == 0) Float.POSITIVE_INFINITY else Float.NaN
            else -> Float.fromBits(((exponent + 112) shl 23) or (mantissa shl 13))
        }
        return if (sign == 1) -magnitude else magnitude
    }

    private fun floats(values: FloatArray, shape: List<Int>): MLMultiArray {
        val array = newArray(shape, MLMultiArrayDataTypeFloat32, values.size)
        val destination = array.dataPointer ?: error("MLMultiArray carries no data pointer")
        values.usePinned { memcpy(destination, it.addressOf(0), (values.size * 4).convert()) }
        return array
    }

    private fun ints(values: IntArray, shape: List<Int>): MLMultiArray {
        val array = newArray(shape, MLMultiArrayDataTypeInt32, values.size)
        val destination = array.dataPointer ?: error("MLMultiArray carries no data pointer")
        values.usePinned { memcpy(destination, it.addressOf(0), (values.size * 4).convert()) }
        return array
    }

    /**
     * Bulk-copied through `dataPointer` rather than element by element.
     *
     * The tensors here are large — 196 608 floats of pixel values, 102 400 of prefill mask — and
     * `setObject(NSNumber(...), atIndexedSubscript:)` would box every element.
     */
    private fun newArray(shape: List<Int>, dataType: MLMultiArrayDataType, expected: Int): MLMultiArray {
        val elements = shape.fold(1) { acc, n -> acc * n }
        require(elements == expected) { "shape $shape holds $elements elements, got $expected" }
        return MLMultiArray.create(shape.map { NSNumber(int = it) }, dataType, null)
            ?: error("failed to allocate an MLMultiArray of shape $shape")
    }

    private companion object {
        const val LAYERS = 12
        const val VISUAL_OUTPUT = "visual_features"
    }
}
