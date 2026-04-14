package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import platform.CoreML.*
import platform.Foundation.*

actual class OcrEncoderRunner {
    private var model: MLModel? = null

    actual fun initialize() {
        val modelUrl = NSBundle.mainBundle.URLForResource(
            PlatformModelPaths.ENCODER_MODEL_PATH, withExtension = "mlmodelc"
        ) ?: error("Encoder CoreML model not found in bundle")
        model = MLModel.modelWithContentsOfURL(modelUrl, error = null)
            ?: error("Failed to load encoder CoreML model")
    }

    actual fun run(pixelValues: FloatArray): FloatArray {
        val size = MangaOcrConfig.IMAGE_SIZE
        val shape = listOf(1, 3, size, size).map { NSNumber(int = it) }
        val inputArray = MLMultiArray(
            shape = shape as List<NSNumber>,
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create input MLMultiArray")
        for (i in pixelValues.indices) {
            inputArray.setObject(NSNumber(float = pixelValues[i]), atIndexedSubscript = i.toLong())
        }
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf("pixel_values" to inputArray) as Map<String, Any>,
            error = null,
        ) ?: error("Failed to create feature provider")
        val result = model!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("Encoder inference failed")
        val outputFeature = result.featureValueForName("last_hidden_state")
            ?: error("Encoder output 'last_hidden_state' not found")
        val outputArray = outputFeature.multiArrayValue
            ?: error("Encoder output is not a multi array")
        val count = outputArray.count.toInt()
        return FloatArray(count) { outputArray.objectAtIndexedSubscript(it.toLong()).floatValue }
    }

    actual fun close() {
        model = null
    }
}
