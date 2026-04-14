package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import platform.CoreML.*
import platform.Foundation.*

actual class OcrDecoderRunner {
    private var crossAttnInitModel: MLModel? = null
    private var decoderWithPastModel: MLModel? = null

    actual fun initialize() {
        crossAttnInitModel = loadModel("cross_attn_init")
        decoderWithPastModel = loadModel("decoder_with_past")
    }

    actual fun run(inputIds: IntArray, seqLen: Int, encoderOutput: FloatArray): FloatArray {
        error("Legacy decoder removed. Use beginSequence() + stepWithKvCache() instead.")
    }

    // ---- KV Cache methods ----

    actual fun beginSequence(encoderOutput: FloatArray): KvCacheState {
        val encoderArray = floatArrayToMLMultiArray(
            encoderOutput,
            listOf(1, MangaOcrConfig.ENCODER_SEQ_LENGTH, MangaOcrConfig.ENCODER_HIDDEN_SIZE),
        )

        // Run cross_attn_init to compute cross-attention KV
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf("encoder_hidden_states" to encoderArray) as Map<Any?, *>,
            error = null,
        ) ?: error("Failed to create cross_attn_init feature provider")

        val result = crossAttnInitModel!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("cross_attn_init inference failed")

        val numLayers = MangaOcrConfig.DECODER_NUM_LAYERS
        val crossKeys = Array(numLayers) { layer ->
            result.featureValueForName("cross_key_layer$layer")?.multiArrayValue
                ?: error("cross_key_layer$layer not found")
        }
        val crossValues = Array(numLayers) { layer ->
            result.featureValueForName("cross_value_layer$layer")?.multiArrayValue
                ?: error("cross_value_layer$layer not found")
        }

        // Self-attention KV starts with seq_len=1 (CoreML RangeDim lower_bound=1)
        val emptySelfKeys = Array(numLayers) {
            createZeroMLMultiArray(listOf(1, MangaOcrConfig.DECODER_NUM_HEADS, 1, MangaOcrConfig.DECODER_HEAD_DIM))
        }
        val emptySelfValues = Array(numLayers) {
            createZeroMLMultiArray(listOf(1, MangaOcrConfig.DECODER_NUM_HEADS, 1, MangaOcrConfig.DECODER_HEAD_DIM))
        }

        return KvCacheState(encoderArray, emptySelfKeys, emptySelfValues, crossKeys, crossValues)
    }

    actual fun stepWithKvCache(tokenId: Int, kvCache: KvCacheState): DecoderStepResult {
        val numLayers = MangaOcrConfig.DECODER_NUM_LAYERS
        val vocabSize = MangaOcrConfig.VOCAB_SIZE

        // Build input_ids [1, 1]
        val idsArray = MLMultiArray(
            shape = listOf(NSNumber(int = 1), NSNumber(int = 1)),
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeInt32,
            error = null,
        ) ?: error("Failed to create input_ids MLMultiArray")
        idsArray.setObject(NSNumber(int = tokenId), atIndexedSubscript = 0L)

        // Build feature dict
        val dict = mutableMapOf<Any?, Any>(
            "input_ids" to idsArray,
            "encoder_hidden_states" to kvCache.encoderArray,
        )
        for (layer in 0 until numLayers) {
            dict["past_self_key_layer$layer"] = kvCache.selfKeys[layer]
            dict["past_self_value_layer$layer"] = kvCache.selfValues[layer]
            dict["past_cross_key_layer$layer"] = kvCache.crossKeys[layer]
            dict["past_cross_value_layer$layer"] = kvCache.crossValues[layer]
        }

        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = dict as Map<Any?, *>,
            error = null,
        ) ?: error("Failed to create decoder feature provider")

        val result = decoderWithPastModel!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("decoder_with_past inference failed")

        // Extract logits [1, 1, 6144]
        val logitsArray = result.featureValueForName("logits")?.multiArrayValue
            ?: error("logits not found")
        val logits = FloatArray(vocabSize) {
            logitsArray.objectAtIndexedSubscript(it.toLong()).floatValue
        }

        // Extract updated self-attention KV
        val newSelfKeys = Array(numLayers) { layer ->
            result.featureValueForName("present_self_key_layer$layer")?.multiArrayValue
                ?: error("present_self_key_layer$layer not found")
        }
        val newSelfValues = Array(numLayers) { layer ->
            result.featureValueForName("present_self_value_layer$layer")?.multiArrayValue
                ?: error("present_self_value_layer$layer not found")
        }

        val newKv = KvCacheState(kvCache.encoderArray, newSelfKeys, newSelfValues, kvCache.crossKeys, kvCache.crossValues)
        return DecoderStepResult(logits, newKv)
    }

    actual fun close() {
        crossAttnInitModel = null
        decoderWithPastModel = null
    }

    // ---- Helpers ----

    private fun loadModel(name: String): MLModel {
        val url = NSBundle.mainBundle.URLForResource(name, withExtension = "mlmodelc", subdirectory = "mangaocr")
            ?: error("CoreML model '$name' not found in bundle")
        return MLModel.modelWithContentsOfURL(url, error = null)
            ?: error("Failed to load CoreML model '$name'")
    }

    private fun floatArrayToMLMultiArray(data: FloatArray, shape: List<Int>): MLMultiArray {
        val nsShape = shape.map { NSNumber(int = it) }
        val array = MLMultiArray(
            shape = nsShape,
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create MLMultiArray")
        for (i in data.indices) {
            array.setObject(NSNumber(float = data[i]), atIndexedSubscript = i.toLong())
        }
        return array
    }

    private fun createZeroMLMultiArray(shape: List<Int>): MLMultiArray {
        val nsShape = shape.map { NSNumber(int = it) }
        val array = MLMultiArray(
            shape = nsShape,
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create zero MLMultiArray")
        val count = shape.fold(1) { acc, v -> acc * v }
        for (i in 0 until count) {
            array.setObject(NSNumber(float = 0f), atIndexedSubscript = i.toLong())
        }
        return array
    }
}
