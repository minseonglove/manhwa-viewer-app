package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import platform.CoreML.*
import platform.Foundation.*

actual class YoloRunner {
    private var model: MLModel? = null

    actual fun initialize() {
        val modelUrl = NSBundle.mainBundle.URLForResource(
            "yolo_detector", withExtension = "mlmodelc", subdirectory = "mangaocr"
        ) ?: error("YOLO CoreML model not found in bundle")
        model = MLModel.modelWithContentsOfURL(modelUrl, error = null)
            ?: error("Failed to load YOLO CoreML model")
    }

    actual fun run(imageData: FloatArray): FloatArray {
        val inputSize = MangaOcrConfig.YOLO_INPUT_SIZE
        val shape = listOf(1, 3, inputSize, inputSize).map { NSNumber(int = it) }
        val inputArray = MLMultiArray(
            shape = shape as List<NSNumber>,
            dataType = MLMultiArrayDataType.MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create input MLMultiArray")
        for (i in imageData.indices) {
            inputArray.setObject(NSNumber(float = imageData[i]), atIndexedSubscript = i.toLong())
        }
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf("images" to inputArray) as Map<String, Any>,
            error = null,
        ) ?: error("Failed to create feature provider")
        val result = model!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("YOLO inference failed")
        val outputFeature = result.featureValueForName("output0")
            ?: error("YOLO output 'output0' not found")
        val outputArray = outputFeature.multiArrayValue
            ?: error("YOLO output is not a multi array")
        val totalElements = MangaOcrConfig.YOLO_MAX_DETECTIONS * 6
        return FloatArray(totalElements) { outputArray.objectAtIndexedSubscript(it.toLong()).floatValue }
    }

    actual fun close() {
        model = null
    }
}
