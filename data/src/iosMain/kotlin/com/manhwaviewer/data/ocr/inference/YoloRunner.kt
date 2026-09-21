package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.PlatformModelPaths
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreML.MLComputeUnits
import platform.CoreML.MLComputeUnitsAll
import platform.CoreML.MLDictionaryFeatureProvider
import platform.CoreML.MLModel
import platform.CoreML.MLModelConfiguration
import platform.CoreML.MLMultiArray
import platform.CoreML.MLMultiArrayDataTypeFloat32
import platform.CoreML.create
import platform.CoreML.objectForKeyedSubscript
import platform.Foundation.NSBundle
import platform.Foundation.NSNumber
import platform.posix.memcpy

/**
 * [computeUnits] exists for the simulator: there the default routes this graph to an emulated GPU
 * that returns all zeros, while CPU-only matches the Mac's output. Devices keep the default.
 */
actual class YoloRunner(private val computeUnits: MLComputeUnits = MLComputeUnitsAll) {
    private var model: MLModel? = null
    private var outputName: String? = null

    actual fun initialize() {
        val modelUrl = NSBundle.mainBundle.URLForResource(
            "yolo_detector", withExtension = "mlmodelc", subdirectory = "mangaocr"
        ) ?: error("YOLO CoreML model not found in bundle")
        // Not `apply { setComputeUnits(computeUnits) }`: inside that block the name resolves to the configuration's own property.
        val config = MLModelConfiguration()
        config.setComputeUnits(computeUnits)
        val loaded = MLModel.modelWithContentsOfURL(modelUrl, config, null)
            ?: error("Failed to load YOLO CoreML model")
        // The converter names the output after an internal graph node (e.g. "var_2007"), so the name changes with every re-export.
        val outputs = loaded.modelDescription.outputDescriptionsByName.keys.map { it.toString() }
        outputName = outputs.singleOrNull() ?: error("YOLO CoreML model declares ${outputs.size} outputs: $outputs")
        model = loaded
    }

    actual fun run(imageData: FloatArray): FloatArray {
        val inputSize = MangaOcrConfig.YOLO_INPUT_SIZE
        val shape = listOf(1, 3, inputSize, inputSize).map { NSNumber(int = it) }
        val inputArray = MLMultiArray.create(
            shape = shape,
            dataType = MLMultiArrayDataTypeFloat32,
            error = null,
        ) ?: error("Failed to create input MLMultiArray")
        // One copy into the array's storage instead of boxing 1.2M values into NSNumbers.
        imageData.usePinned { pinned ->
            memcpy(inputArray.dataPointer, pinned.addressOf(0), (imageData.size * Float.SIZE_BYTES).toULong())
        }
        val featureProvider = MLDictionaryFeatureProvider(
            dictionary = mapOf("images" to inputArray) as Map<Any?, *>,
            error = null,
        ) ?: error("Failed to create feature provider")
        val result = model!!.predictionFromFeatures(featureProvider, error = null)
            ?: error("YOLO inference failed")
        val name = outputName ?: error("initialize() has not run")
        val outputFeature = result.featureValueForName(name)
            ?: error("YOLO output '$name' not found")
        val outputArray = outputFeature.multiArrayValue()
            ?: error("YOLO output is not a multi array")
        // CoreML hands the [1, 300, 6] output back with padded rows (strides [9600, 32, 1]), so a linear
        // index only lands on real values for the first row. Subscripting by [0, row, column] follows the strides.
        val zero = NSNumber(int = 0)
        val rows = (0 until MangaOcrConfig.YOLO_MAX_DETECTIONS).map { NSNumber(int = it) }
        val columns = (0 until 6).map { NSNumber(int = it) }
        return FloatArray(MangaOcrConfig.YOLO_MAX_DETECTIONS * 6) {
            (outputArray.objectForKeyedSubscript(listOf(zero, rows[it / 6], columns[it % 6])) as NSNumber).floatValue
        }
    }

    actual fun close() {
        model = null
        outputName = null
    }
}
