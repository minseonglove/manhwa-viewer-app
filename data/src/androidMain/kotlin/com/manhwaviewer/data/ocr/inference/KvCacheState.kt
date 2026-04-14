package com.manhwaviewer.data.ocr.inference

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android KV cache state using fp16 ByteBuffers for zero-copy ONNX Runtime I/O.
 * Cross-attention KV is shared (reference) across beam copies.
 */
actual class KvCacheState(
    /** Cached encoder output in fp16 format */
    val encoderFp16: ByteBuffer,
    /** Self-attention keys per layer [DECODER_NUM_LAYERS] - grows with sequence */
    val selfKeys: Array<ByteBuffer>,
    /** Self-attention values per layer [DECODER_NUM_LAYERS] */
    val selfValues: Array<ByteBuffer>,
    /** Cross-attention keys per layer [DECODER_NUM_LAYERS] - fixed, shared */
    val crossKeys: Array<ByteBuffer>,
    /** Cross-attention values per layer [DECODER_NUM_LAYERS] - fixed, shared */
    val crossValues: Array<ByteBuffer>,
) {
    actual fun copySelfAttn(): KvCacheState {
        return KvCacheState(
            encoderFp16 = encoderFp16,
            selfKeys = selfKeys.map { it.copyDirect() }.toTypedArray(),
            selfValues = selfValues.map { it.copyDirect() }.toTypedArray(),
            crossKeys = crossKeys,     // shared reference
            crossValues = crossValues, // shared reference
        )
    }
}

private fun ByteBuffer.copyDirect(): ByteBuffer {
    val src = duplicate().apply { rewind() }
    val copy = ByteBuffer.allocateDirect(src.remaining()).order(ByteOrder.nativeOrder())
    copy.put(src)
    copy.flip()
    return copy
}
