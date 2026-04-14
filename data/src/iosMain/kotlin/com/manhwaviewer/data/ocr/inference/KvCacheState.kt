package com.manhwaviewer.data.ocr.inference

/**
 * iOS KV cache state stub. KV cache is not yet supported on iOS (requires CoreML model conversion).
 * The beginSequence/stepWithKvCache methods throw UnsupportedOperationException on iOS.
 */
actual class KvCacheState(
    /** Cached encoder output as MLMultiArray-compatible FloatArray */
    val cachedEncoderOutput: FloatArray,
) {
    actual fun copySelfAttn(): KvCacheState = KvCacheState(cachedEncoderOutput)
}
