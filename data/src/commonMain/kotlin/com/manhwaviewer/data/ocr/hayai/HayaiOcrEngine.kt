package com.manhwaviewer.data.ocr.hayai

/**
 * Hayai OCR's greedy decode loop, shared by both platforms.
 *
 * A port of the reference engine's `generate`: the same host tensors, the same stopping rules,
 * the same repetition penalty. Only graph execution is platform-specific, behind [HayaiGraphs].
 *
 * [recognize] takes a crop that has already been resized to the grid [HayaiHost.scaledSize]
 * chose, because resizing is a platform image operation.
 */
class HayaiOcrEngine(
    private val graphs: HayaiGraphs,
    private val tokenizer: HayaiTokenizer,
    private val positionBase: FloatArray,
    private val bosId: Int,
    private val eosId: Int,
    private val padId: Int,
) {

    fun recognize(
        rgb: FloatArray,
        patchRows: Int,
        patchCols: Int,
        maxNewTokens: Int = 64,
        repetitionPenalty: Float = 1f,
    ): String {
        require(maxNewTokens in 1..HayaiHost.MAX_TEXT) {
            "maxNewTokens $maxNewTokens is outside 1..${HayaiHost.MAX_TEXT}, the graph's text budget"
        }
        val visual = graphs.encode(
            HayaiHost.encoderInput(rgb, patchRows, patchCols),
            HayaiHost.attentionMask(patchRows, patchCols),
            HayaiHost.positionEmbeddings(positionBase, patchRows, patchCols),
        )
        val (cos, sin) = HayaiHost.rope(patchRows, patchCols)

        // prefill 이 성공하면 visual 은 그쪽이 가져간다. 실패하면 여기서 놓아줘야 남지 않는다.
        var step = try {
            graphs.prefill(visual, bosId, cos, sin, HayaiHost.prefillMask())
        } catch (t: Throwable) {
            visual.close()
            throw t
        }

        try {
            val generated = mutableListOf<Int>()
            val seen = mutableSetOf(bosId)
            var next = pickNext(step.logits, seen, repetitionPenalty)

            while (next != eosId && next != padId && generated.size < maxNewTokens) {
                generated += next
                seen += next
                if (generated.size >= maxNewTokens) break
                val pos = HayaiHost.MAX_PATCHES + generated.size
                step = graphs.decodeStep(
                    tokenId = next,
                    cos = rowAt(cos, pos),
                    sin = rowAt(sin, pos),
                    mask = HayaiHost.decodeMask(pos),
                    pos = pos,
                    cache = step.cache,
                )
                next = pickNext(step.logits, seen, repetitionPenalty)
            }
            return tokenizer.decode(generated)
        } finally {
            // 마지막 스텝의 캐시는 소비하는 다음 스텝이 없다.
            step.cache.close()
        }
    }

    /** Releases the graphs this engine was given. */
    fun close() {
        graphs.close()
    }

    /** One rotary row, the slice the decode graph takes for the slot it is writing. */
    private fun rowAt(table: FloatArray, pos: Int): FloatArray =
        table.copyOfRange(pos * HayaiHost.D_AXIS, (pos + 1) * HayaiHost.D_AXIS)

    /**
     * Greedy pick, after damping the tokens already emitted.
     *
     * The penalty divides positive logits and multiplies negative ones, so it pushes a repeat
     * towards the bottom either way. A penalty of 1 leaves the logits alone, which is what every
     * measurement in this investigation ran with.
     */
    private fun pickNext(logits: FloatArray, seen: Set<Int>, penalty: Float): Int {
        var bestId = 0
        var best = Float.NEGATIVE_INFINITY
        for (id in logits.indices) {
            val raw = logits[id]
            val value = if (penalty != 1f && id in seen) {
                if (raw < 0f) raw * penalty else raw / penalty
            } else {
                raw
            }
            if (value > best) {
                best = value
                bestId = id
            }
        }
        return bestId
    }
}
