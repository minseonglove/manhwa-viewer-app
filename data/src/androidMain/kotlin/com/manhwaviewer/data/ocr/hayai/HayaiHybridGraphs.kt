package com.manhwaviewer.data.ocr.hayai

/**
 * Hayai with the vision encoder on LiteRT and prefill/decode on ONNX Runtime.
 *
 * The split follows where each graph runs fastest. The encoder is the heaviest graph on the CPU
 * and the GPU runs it 2.5–3x faster. The decode steps are the reverse: batch-1 token steps are a
 * few hundred tiny kernels each, and the GPU spends them on dispatch (13–31% busy) while ORT on
 * the CPU takes a third of the time. Prefill stays on ORT with decode so the two keep sharing one
 * weight blob.
 *
 * Concurrent recognitions queue for the encoder on the LiteRT thread while their prefill and
 * decode run in parallel on ORT.
 */
class HayaiHybridGraphs(
    private val encoder: HayaiLiteRtEncoder,
    private val decoder: HayaiOnnxGraphs,
) : HayaiGraphs {

    override fun encode(pixelValues: FloatArray, attentionMask: IntArray, posEmbeds: FloatArray): HayaiVisualFeatures =
        HayaiFloatFeatures(encoder.encode(pixelValues, attentionMask, posEmbeds))

    override fun prefill(visual: HayaiVisualFeatures, tokenId: Int, cos: FloatArray, sin: FloatArray, mask: FloatArray): HayaiStepResult =
        decoder.prefill(visual, tokenId, cos, sin, mask)

    override fun decodeStep(
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
        pos: Int,
        cache: HayaiKvCache,
    ): HayaiStepResult = decoder.decodeStep(tokenId, cos, sin, mask, pos, cache)

    override fun close() {
        encoder.close()
        decoder.close()
    }
}
