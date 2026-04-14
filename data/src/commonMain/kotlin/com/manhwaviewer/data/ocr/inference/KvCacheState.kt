package com.manhwaviewer.data.ocr.inference

/**
 * Platform-specific KV cache state holding decoder attention tensors.
 * Self-attention KV grows with each decoded token, cross-attention KV is fixed.
 */
expect class KvCacheState {
    /** Deep copy of self-attention KV for beam branching. Cross-attention KV is shared. */
    fun copySelfAttn(): KvCacheState
}

data class DecoderStepResult(
    val logits: FloatArray,
    val kvCache: KvCacheState,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecoderStepResult) return false
        return logits.contentEquals(other.logits) && kvCache == other.kvCache
    }

    override fun hashCode(): Int {
        var result = logits.contentHashCode()
        result = 31 * result + kvCache.hashCode()
        return result
    }
}
