package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.MangaOcrConfig
import platform.CoreML.MLMultiArray
import platform.CoreML.MLMultiArrayDataType
import platform.Foundation.NSNumber

/**
 * iOS KV cache state using CoreML MLMultiArray tensors.
 * Cross-attention KV is shared across beam copies.
 */
actual class KvCacheState(
    /** Cached encoder output as MLMultiArray [1, 197, 192] */
    val encoderArray: MLMultiArray,
    /** Self-attention keys per layer - grows with sequence */
    val selfKeys: Array<MLMultiArray>,
    /** Self-attention values per layer */
    val selfValues: Array<MLMultiArray>,
    /** Cross-attention keys per layer - fixed, shared */
    val crossKeys: Array<MLMultiArray>,
    /** Cross-attention values per layer - fixed, shared */
    val crossValues: Array<MLMultiArray>,
) {
    actual fun copySelfAttn(): KvCacheState {
        return KvCacheState(
            encoderArray = encoderArray,
            selfKeys = selfKeys.map { it.deepCopy() }.toTypedArray(),
            selfValues = selfValues.map { it.deepCopy() }.toTypedArray(),
            crossKeys = crossKeys,     // shared
            crossValues = crossValues, // shared
        )
    }
}

private fun MLMultiArray.deepCopy(): MLMultiArray {
    val shape = (0 until this.shape.count().toInt()).map {
        this.shape.objectAtIndex(it.toULong()) as NSNumber
    }
    val copy = MLMultiArray(
        shape = shape,
        dataType = MLMultiArrayDataType.MLMultiArrayDataTypeFloat32,
        error = null,
    ) ?: error("Failed to create MLMultiArray copy")
    val count = this.count.toInt()
    for (i in 0 until count) {
        copy.setObject(
            this.objectAtIndexedSubscript(i.toLong()),
            atIndexedSubscript = i.toLong(),
        )
    }
    return copy
}
