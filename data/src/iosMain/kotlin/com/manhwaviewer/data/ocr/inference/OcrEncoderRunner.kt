package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import platform.CoreML.MLDictionaryFeatureProvider
import platform.CoreML.MLModel
import platform.CoreML.MLMultiArray
import platform.CoreML.MLMultiArrayDataTypeFloat32
import platform.CoreML.create
import platform.CoreML.objectAtIndexedSubscript
import platform.CoreML.setObject
import platform.Foundation.NSBundle
import platform.Foundation.NSNumber

actual class OcrEncoderRunner {
    private var model: MLModel? = null

    actual fun initialize() {
        val modelUrl = NSBundle.mainBundle.URLForResource(
            "encoder", withExtension = "mlmodelc", subdirectory = "mangaocr"
        ) ?: error("Encoder CoreML model not found in bundle")
        model = MLModel.modelWithContentsOfURL(modelUrl, error = null)
            ?: error("Failed to load encoder CoreML model")
    }

    actual fun run(pixelValues: FloatArray): FloatArray {
        val size = MangaOcrConfig.IMAGE_SIZE
        val shape = listOf(1, 3, size, size).map { NSNumber(int = it) }
        val inputArray = MLMultiArray.create(
            shape = shape,
            dataType = MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create input MLMultiArray")
        for (i in pixelValues.indices) {
            inputArray.setObject(NSNumber(float = pixelValues[i]), atIndexedSubscript = i.toLong())
        }
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf("pixel_values" to inputArray) as Map<Any?, *>,
            error = null,
        ) ?: error("Failed to create feature provider")
        val result = model!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("Encoder inference failed")
        val outputFeature = result.featureValueForName("last_hidden_state")
            ?: error("Encoder output 'last_hidden_state' not found")
        val outputArray = outputFeature.multiArrayValue()
            ?: error("Encoder output is not a multi array")
        val count = outputArray.count().toInt()
        return FloatArray(count) { (outputArray.objectAtIndexedSubscript(it.toLong()) as NSNumber).floatValue }
    }

    actual fun runToEncoderOutput(pixelValues: FloatArray): EncoderOutput {
        val size = MangaOcrConfig.IMAGE_SIZE
        val shape = listOf(1, 3, size, size).map { NSNumber(int = it) }
        val inputArray = MLMultiArray.create(
            shape = shape,
            dataType = MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create input MLMultiArray")
        for (i in pixelValues.indices) {
            inputArray.setObject(NSNumber(float = pixelValues[i]), atIndexedSubscript = i.toLong())
        }
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf("pixel_values" to inputArray) as Map<Any?, *>,
            error = null,
        ) ?: error("Failed to create feature provider")
        val result = model!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("Encoder inference failed")
        val outputFeature = result.featureValueForName("last_hidden_state")
            ?: error("Encoder output 'last_hidden_state' not found")
        val outputArray = outputFeature.multiArrayValue()
            ?: error("Encoder output is not a multi array")
        return EncoderOutput(outputArray)
    }

    actual fun close() {
        model = null
    }
}
