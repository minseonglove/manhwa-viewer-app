package com.manhwaviewer.data.ocr.hayai

/**
 * The encoder's output, held in whatever form the platform runtime produced it.
 *
 * Opaque on purpose: at (1, 256, 768) it is 786 KB, and prefill is the only thing that reads it.
 *
 * [close] exists because the runtime may be holding native memory that garbage collection does
 * not see. Whoever receives one owns it.
 */
interface HayaiVisualFeatures {
    fun close()
}

/**
 * One decode step's KV cache, held in whatever form the platform runtime produced it.
 *
 * Opaque on purpose, and more sharply than [HayaiVisualFeatures]: the cache is 24 tensors of
 * (1, 2, 320, 64), about 3.9 MB, and it is read and rewritten on *every* step. Copying it into
 * Kotlin arrays per step would cost more than the graphs themselves.
 *
 * Each step releases the cache it consumed, but the last step's cache has no consumer — the
 * caller must release that one, or a recognition's worth of native memory stays behind.
 */
interface HayaiKvCache {
    fun close()
}

/** Logits for the next token, plus the cache to carry into the following step. */
class HayaiStepResult(val logits: FloatArray, val cache: HayaiKvCache)

/**
 * The three Hayai graphs, as the shared decode loop uses them.
 *
 * An interface rather than an `expect class` so the loop can be driven in commonTest without a
 * model; the platform implementations are what `expect`/`actual` resolve. Everything crossing
 * this boundary is either small (masks, one rotary row) or opaque (features, cache).
 *
 * [decodeStep] takes `pos` as an integer, not a prepared tensor: LiteRT's graph wants the scalar
 * while CoreML's wants the one-hot `HayaiHost.writeMask(pos)`, and that difference belongs to the
 * platform implementation rather than to the loop.
 */
interface HayaiGraphs {

    fun encode(
        pixelValues: FloatArray,
        attentionMask: IntArray,
        posEmbeds: FloatArray,
    ): HayaiVisualFeatures

    fun prefill(
        visual: HayaiVisualFeatures,
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
    ): HayaiStepResult

    fun decodeStep(
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
        pos: Int,
        cache: HayaiKvCache,
    ): HayaiStepResult

    fun close()
}
