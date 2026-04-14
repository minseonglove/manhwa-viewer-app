package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import platform.CoreML.*
import platform.Foundation.*

actual class OcrDecoderRunner {
    private var model: MLModel? = null

    actual fun initialize() {
        val modelUrl = NSBundle.mainBundle.URLForResource(
            PlatformModelPaths.DECODER_MODEL_PATH, withExtension = "mlmodelc"
        ) ?: error("Decoder CoreML model not found in bundle")
        model = MLModel.modelWithContentsOfURL(modelUrl, error = null)
            ?: error("Failed to load decoder CoreML model")
    }

    actual fun run(inputIds: IntArray, seqLen: Int, encoderOutput: FloatArray): FloatArray {
        // Input IDs
        val idsShape = listOf(1, seqLen).map { NSNumber(int = it) }
        val idsArray = MLMultiArray(
            shape = idsShape as List<NSNumber>,
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeInt32,
            error = null,
        ) ?: error("Failed to create input_ids MLMultiArray")
        for (i in 0 until seqLen) {
            idsArray.setObject(NSNumber(int = inputIds[i]), atIndexedSubscript = i.toLong())
        }
        // Encoder hidden states
        val encShape = listOf(
            1,
            MangaOcrConfig.ENCODER_SEQ_LENGTH,
            MangaOcrConfig.ENCODER_HIDDEN_SIZE,
        ).map { NSNumber(int = it) }
        val encArray = MLMultiArray(
            shape = encShape as List<NSNumber>,
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create encoder_hidden_states MLMultiArray")
        for (i in encoderOutput.indices) {
            encArray.setObject(NSNumber(float = encoderOutput[i]), atIndexedSubscript = i.toLong())
        }
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf(
                "input_ids" to idsArray,
                "encoder_hidden_states" to encArray,
            ) as Map<String, Any>,
            error = null,
        ) ?: error("Failed to create feature provider")
        val result = model!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("Decoder inference failed")
        val outputFeature = result.featureValueForName("logits")
            ?: error("Decoder output 'logits' not found")
        val outputArray = outputFeature.multiArrayValue
            ?: error("Decoder output is not a multi array")
        // Extract logits for last position only
        val vocabSize = MangaOcrConfig.VOCAB_SIZE
        val lastPosOffset = (seqLen - 1) * vocabSize
        return FloatArray(vocabSize) { outputArray.objectAtIndexedSubscript((lastPosOffset + it).toLong()).floatValue }
    }

    actual fun close() {
        model = null
    }
}
