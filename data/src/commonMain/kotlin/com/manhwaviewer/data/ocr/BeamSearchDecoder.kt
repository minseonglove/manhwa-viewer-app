package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.inference.DecoderStepResult
import com.manhwaviewer.data.ocr.inference.KvCacheState
import kotlin.math.exp
import kotlin.math.ln

internal data class BeamCandidate(
    val tokenIds: IntArray,
    val seqLen: Int,
    val score: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BeamCandidate) return false
        return tokenIds.contentEquals(other.tokenIds) && seqLen == other.seqLen && score == other.score
    }

    override fun hashCode(): Int {
        var result = tokenIds.contentHashCode()
        result = 31 * result + seqLen
        result = 31 * result + score.hashCode()
        return result
    }
}

/**
 * Beam search decoding for MangaOCR.
 *
 * @param beamWidth number of beams to keep at each step
 * @param maxLength maximum sequence length
 * @param vocabSize vocabulary size for logits
 * @param startTokenId decoder start token
 * @param eosTokenId end-of-sequence token
 * @param decoderStep platform-specific decoder: given (tokenIds, seqLen), returns FloatArray of logits for the last position
 */
internal fun beamSearchDecode(
    beamWidth: Int,
    maxLength: Int,
    vocabSize: Int,
    startTokenId: Int,
    eosTokenId: Int,
    decoderStep: (inputIds: IntArray, seqLen: Int) -> FloatArray,
): List<Int> {
    var beams = listOf(
        BeamCandidate(
            tokenIds = IntArray(maxLength + 1).also { it[0] = startTokenId },
            seqLen = 1,
            score = 0f,
        )
    )
    val completed = mutableListOf<BeamCandidate>()

    for (step in 0 until maxLength) {
        val candidates = mutableListOf<Triple<BeamCandidate, Int, Float>>()

        for (beam in beams) {
            val logits = decoderStep(beam.tokenIds, beam.seqLen)
            applyRepetitionPenalty(logits, beam.tokenIds, beam.seqLen, MangaOcrConfig.REPETITION_PENALTY)
            applyNgramBlocking(logits, beam.tokenIds, beam.seqLen, MangaOcrConfig.NO_REPEAT_NGRAM_SIZE)
            applyTemperature(logits, MangaOcrConfig.TEMPERATURE)
            val logProbs = logSoftmax(logits)
            val topIndices = topK(logProbs, beamWidth)

            for (idx in topIndices) {
                candidates.add(Triple(beam, idx, beam.score + logProbs[idx]))
            }
        }

        candidates.sortByDescending { it.third }

        val newBeams = mutableListOf<BeamCandidate>()
        for ((parent, tokenId, newScore) in candidates) {
            if (newBeams.size >= beamWidth && completed.isNotEmpty()) break
            if (newBeams.size >= beamWidth) continue

            val newTokenIds = parent.tokenIds.copyOf()
            newTokenIds[parent.seqLen] = tokenId
            val candidate = BeamCandidate(newTokenIds, parent.seqLen + 1, newScore)

            if (tokenId == eosTokenId) {
                completed.add(candidate)
            } else {
                newBeams.add(candidate)
            }
        }

        // Early stopping: prune active beams that can't beat the best completed sequence.
        // Log-prob scores are negative and only decrease, so an active beam whose
        // normalized score is already worse than the best completed can never catch up.
        if (completed.isNotEmpty()) {
            val bestCompletedNormScore = completed.maxOf { lengthNormalizedScore(it) }
            newBeams.removeAll { lengthNormalizedScore(it) <= bestCompletedNormScore }
        }

        beams = newBeams
        if (beams.isEmpty()) break
    }

    val best = (completed + beams).maxByOrNull { lengthNormalizedScore(it) }
        ?: return listOf(startTokenId)
    return (0 until best.seqLen).map { best.tokenIds[it] }
}

private const val LENGTH_PENALTY_ALPHA = 0.6f

private fun lengthNormalizedScore(candidate: BeamCandidate): Float {
    val penalty = exp(LENGTH_PENALTY_ALPHA * ln((5f + candidate.seqLen) / 6f))
    return candidate.score / penalty
}

private fun applyRepetitionPenalty(logits: FloatArray, generatedTokenIds: IntArray, seqLen: Int, penalty: Float) {
    for (i in 0 until seqLen) {
        val tokenId = generatedTokenIds[i]
        if (tokenId in logits.indices) {
            if (logits[tokenId] > 0f) logits[tokenId] /= penalty
            else logits[tokenId] *= penalty
        }
    }
}

private fun applyTemperature(logits: FloatArray, temperature: Float) {
    for (i in logits.indices) logits[i] /= temperature
}

/**
 * Block tokens that would create a repeated n-gram.
 * Scans the generated sequence for the current (n-1) prefix and sets logits
 * of any token that previously followed the same prefix to -Infinity.
 */
private fun applyNgramBlocking(logits: FloatArray, tokenIds: IntArray, seqLen: Int, ngramSize: Int) {
    if (ngramSize < 2 || seqLen < ngramSize - 1) return
    val prefixStart = seqLen - (ngramSize - 1)
    for (i in 0..seqLen - ngramSize) {
        var match = true
        for (j in 0 until ngramSize - 1) {
            if (tokenIds[i + j] != tokenIds[prefixStart + j]) {
                match = false
                break
            }
        }
        if (match) {
            val blockedToken = tokenIds[i + ngramSize - 1]
            if (blockedToken in logits.indices) {
                logits[blockedToken] = Float.NEGATIVE_INFINITY
            }
        }
    }
}

private fun logSoftmax(logits: FloatArray): FloatArray {
    var max = logits[0]
    for (i in 1 until logits.size) {
        if (logits[i] > max) max = logits[i]
    }

    val result = FloatArray(logits.size)
    var sumExp = 0.0
    for (i in logits.indices) {
        val shifted = (logits[i] - max).toDouble()
        val e = exp(shifted)
        result[i] = shifted.toFloat()
        sumExp += e
    }

    val logSumExp = ln(sumExp).toFloat()
    for (i in result.indices) {
        result[i] -= logSumExp
    }
    return result
}

/**
 * Beam search with KV cache. Each beam candidate carries its own KV state.
 * Only self-attention KV is copied on beam branch; cross-attention KV is shared.
 */
internal fun beamSearchDecodeWithKvCache(
    beamWidth: Int,
    maxLength: Int,
    vocabSize: Int,
    startTokenId: Int,
    eosTokenId: Int,
    initialKvCache: KvCacheState,
    decoderStep: (tokenId: Int, kvCache: KvCacheState) -> DecoderStepResult,
): List<Int> {
    data class KvBeamCandidate(
        val tokenIds: IntArray,
        val seqLen: Int,
        val score: Float,
        val kvCache: KvCacheState,
    )

    // First step: process start token
    val firstResult = decoderStep(startTokenId, initialKvCache)
    applyRepetitionPenalty(firstResult.logits, intArrayOf(startTokenId), 1, MangaOcrConfig.REPETITION_PENALTY)
    applyNgramBlocking(firstResult.logits, intArrayOf(startTokenId), 1, MangaOcrConfig.NO_REPEAT_NGRAM_SIZE)
    applyTemperature(firstResult.logits, MangaOcrConfig.TEMPERATURE)
    val firstLogProbs = logSoftmax(firstResult.logits)
    val topIndices = topK(firstLogProbs, beamWidth)

    var beams = topIndices.map { idx ->
        val ids = IntArray(maxLength + 1)
        ids[0] = startTokenId
        ids[1] = idx
        KvBeamCandidate(ids, 2, firstLogProbs[idx], firstResult.kvCache.copySelfAttn())
    }

    val completed = mutableListOf<KvBeamCandidate>()

    for (step in 1 until maxLength) {
        val candidates = mutableListOf<Triple<KvBeamCandidate, Int, Float>>()

        for (beam in beams) {
            val lastToken = beam.tokenIds[beam.seqLen - 1]
            val result = decoderStep(lastToken, beam.kvCache)
            applyRepetitionPenalty(result.logits, beam.tokenIds, beam.seqLen, MangaOcrConfig.REPETITION_PENALTY)
            applyNgramBlocking(result.logits, beam.tokenIds, beam.seqLen, MangaOcrConfig.NO_REPEAT_NGRAM_SIZE)
            applyTemperature(result.logits, MangaOcrConfig.TEMPERATURE)
            val logProbs = logSoftmax(result.logits)
            val top = topK(logProbs, beamWidth)

            for (idx in top) {
                candidates.add(Triple(
                    KvBeamCandidate(beam.tokenIds, beam.seqLen, beam.score, result.kvCache),
                    idx,
                    beam.score + logProbs[idx],
                ))
            }
        }

        candidates.sortByDescending { it.third }

        val newBeams = mutableListOf<KvBeamCandidate>()
        for ((parent, tokenId, newScore) in candidates) {
            if (newBeams.size >= beamWidth && completed.isNotEmpty()) break
            if (newBeams.size >= beamWidth) continue

            val newTokenIds = parent.tokenIds.copyOf()
            newTokenIds[parent.seqLen] = tokenId
            val candidate = KvBeamCandidate(
                newTokenIds, parent.seqLen + 1, newScore,
                parent.kvCache.copySelfAttn(),
            )

            if (tokenId == eosTokenId) {
                completed.add(candidate)
            } else {
                newBeams.add(candidate)
            }
        }

        // Early stopping
        if (completed.isNotEmpty()) {
            val bestCompletedNormScore = completed.maxOf { lengthNormalizedScore(BeamCandidate(it.tokenIds, it.seqLen, it.score)) }
            newBeams.removeAll { lengthNormalizedScore(BeamCandidate(it.tokenIds, it.seqLen, it.score)) <= bestCompletedNormScore }
        }

        beams = newBeams
        if (beams.isEmpty()) break
    }

    val allCandidates = completed.map { BeamCandidate(it.tokenIds, it.seqLen, it.score) } +
        beams.map { BeamCandidate(it.tokenIds, it.seqLen, it.score) }
    val best = allCandidates.maxByOrNull { lengthNormalizedScore(it) }
        ?: return listOf(startTokenId)
    return (0 until best.seqLen).map { best.tokenIds[it] }
}

/**
 * Greedy decoding with KV cache and confidence monitoring.
 * Returns null when consecutive low-confidence steps are detected,
 * signalling that beam search should be used instead.
 *
 * Temperature is NOT applied here — it has no effect on argmax results.
 */
internal fun greedyDecodeWithKvCache(
    maxLength: Int,
    vocabSize: Int,
    startTokenId: Int,
    eosTokenId: Int,
    initialKvCache: KvCacheState,
    decoderStep: (tokenId: Int, kvCache: KvCacheState) -> DecoderStepResult,
): List<Int>? {
    val tokenIds = IntArray(maxLength + 1)
    tokenIds[0] = startTokenId
    var seqLen = 1
    var kvCache = initialKvCache
    var consecutiveLowConfidence = 0

    for (step in 0 until maxLength) {
        val result = decoderStep(tokenIds[seqLen - 1], kvCache)
        applyRepetitionPenalty(result.logits, tokenIds, seqLen, MangaOcrConfig.REPETITION_PENALTY)
        applyNgramBlocking(result.logits, tokenIds, seqLen, MangaOcrConfig.NO_REPEAT_NGRAM_SIZE)

        // Monitor confidence: top-1 vs top-2 logit gap
        val logits = result.logits
        var max1 = Float.NEGATIVE_INFINITY
        var max2 = Float.NEGATIVE_INFINITY
        for (v in logits) {
            if (v > max1) { max2 = max1; max1 = v }
            else if (v > max2) { max2 = v }
        }
        val gap = max1 - max2
        if (gap < MangaOcrConfig.CONFIDENCE_GAP_THRESHOLD) {
            consecutiveLowConfidence++
            if (consecutiveLowConfidence >= MangaOcrConfig.CONFIDENCE_CONSECUTIVE_COUNT) {
                println("PageOCR |   Greedy confidence low (gap=${((gap * 100).toInt() / 100.0)}), fallback to beam search")
                return null
            }
        } else {
            consecutiveLowConfidence = 0
        }

        val nextToken = argmax(logits)
        tokenIds[seqLen] = nextToken
        seqLen++
        kvCache = result.kvCache
        if (nextToken == eosTokenId) break
    }
    return (0 until seqLen).map { tokenIds[it] }
}

private fun argmax(values: FloatArray): Int {
    var maxIdx = 0
    for (i in 1 until values.size) {
        if (values[i] > values[maxIdx]) maxIdx = i
    }
    return maxIdx
}

/**
 * Partial selection sort to find top-k indices by value.
 * O(k * n) which is efficient for small k (beam width 3~5) over vocab size 6144.
 */
private fun topK(values: FloatArray, k: Int): IntArray {
    val n = values.size
    val actualK = k.coerceAtMost(n)
    val indices = IntArray(n) { it }

    for (i in 0 until actualK) {
        var maxIdx = i
        for (j in i + 1 until n) {
            if (values[indices[j]] > values[indices[maxIdx]]) {
                maxIdx = j
            }
        }
        val tmp = indices[i]
        indices[i] = indices[maxIdx]
        indices[maxIdx] = tmp
    }
    return indices.copyOf(actualK)
}
