package com.manhwaviewer.data.ocr.inference

expect class OcrDecoderRunner {
    fun initialize()

    /** Legacy decoder step (no KV cache). Kept for backward compatibility. */
    fun run(inputIds: IntArray, seqLen: Int, encoderOutput: FloatArray): FloatArray

    /** Initialize KV cache: compute cross-attention KV + cache encoder output in native format. */
    fun beginSequence(encoderOutput: FloatArray): KvCacheState

    /** Initialize KV cache from raw platform-specific encoder output (zero-copy). */
    fun beginSequence(encoderOutput: EncoderOutput): KvCacheState

    /** Single decoder step with KV cache. Returns logits for the new token + updated KV. */
    fun stepWithKvCache(tokenId: Int, kvCache: KvCacheState): DecoderStepResult

    fun close()
}
