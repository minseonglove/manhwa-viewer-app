package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The greedy loop, driven against a scripted [HayaiGraphs] so the loop's own decisions —
 * when it stops, which slot each step writes, which cache it carries forward — are the
 * things under test rather than the graphs.
 */
class HayaiOcrEngineTest {

    private object Cache1 : HayaiKvCache
    private object Cache2 : HayaiKvCache
    private object Cache3 : HayaiKvCache
    private object Visual : HayaiVisualFeatures

    private val BOS = 2
    private val EOS = 3
    private val PAD = 0
    private val VOCAB = 8

    private fun logitsFor(winner: Int) = FloatArray(VOCAB) { if (it == winner) 10f else 0f }

    private class ScriptedGraphs(
        private val prefillWinner: Int,
        private val decodeWinners: List<Int>,
        private val caches: List<HayaiKvCache>,
        private val vocab: Int,
    ) : HayaiGraphs {
        val decodeTokens = mutableListOf<Int>()
        val decodePositions = mutableListOf<Int>()
        val decodeCaches = mutableListOf<HayaiKvCache>()
        var encodeCalls = 0
        var prefillTokenId = -1
        var closed = false

        override fun encode(pixelValues: FloatArray, attentionMask: IntArray, posEmbeds: FloatArray):
            HayaiVisualFeatures {
            encodeCalls++
            return Visual
        }

        override fun prefill(
            visual: HayaiVisualFeatures, tokenId: Int,
            cos: FloatArray, sin: FloatArray, mask: FloatArray,
        ): HayaiStepResult {
            prefillTokenId = tokenId
            return HayaiStepResult(FloatArray(vocab) { if (it == prefillWinner) 10f else 0f }, caches[0])
        }

        override fun decodeStep(
            tokenId: Int, cos: FloatArray, sin: FloatArray,
            mask: FloatArray, pos: Int, cache: HayaiKvCache,
        ): HayaiStepResult {
            val i = decodeTokens.size
            decodeTokens += tokenId
            decodePositions += pos
            decodeCaches += cache
            val winner = decodeWinners[i]
            return HayaiStepResult(
                FloatArray(vocab) { if (it == winner) 10f else 0f },
                caches[minOf(i + 1, caches.size - 1)],
            )
        }

        override fun close() { closed = true }
    }

    private fun engine(graphs: HayaiGraphs, vocab: Map<Int, String>) = HayaiOcrEngine(
        graphs = graphs,
        tokenizer = HayaiTokenizer(vocab, setOf(PAD, BOS, EOS)),
        positionBase = FloatArray(16 * 16 * 768),
        bosId = BOS, eosId = EOS, padId = PAD,
    )

    /** A 16x16 patch grid is 256x256 pixels; values do not matter to the loop. */
    private fun crop() = FloatArray(256 * 256 * 3)

    private val letters = mapOf(
        PAD to "[PAD]", BOS to "[BOS]", EOS to "[EOS]",
        4 to "a", 5 to "b", 6 to "c", 7 to "d",
    )

    @Test
    fun prefillReceivesTheBosTokenAndTheEncoderRunsOnce() {
        val g = ScriptedGraphs(4, listOf(EOS), listOf(Cache1, Cache2), VOCAB)
        engine(g, letters).recognize(crop(), 16, 16)
        assertEquals(1, g.encodeCalls)
        assertEquals(BOS, g.prefillTokenId)
    }

    @Test
    fun eachDecodeStepWritesTheNextCacheSlotStartingAt257() {
        val g = ScriptedGraphs(4, listOf(5, 6, EOS), listOf(Cache1, Cache2, Cache3), VOCAB)
        engine(g, letters).recognize(crop(), 16, 16)
        assertEquals(listOf(257, 258, 259), g.decodePositions)
    }

    @Test
    fun eachDecodeStepIsFedTheTokenTheStepBeforeProduced() {
        val g = ScriptedGraphs(4, listOf(5, 6, EOS), listOf(Cache1, Cache2, Cache3), VOCAB)
        engine(g, letters).recognize(crop(), 16, 16)
        assertEquals(listOf(4, 5, 6), g.decodeTokens)
    }

    @Test
    fun theCacheFromEachStepIsCarriedIntoTheNext() {
        val g = ScriptedGraphs(4, listOf(5, EOS), listOf(Cache1, Cache2, Cache3), VOCAB)
        engine(g, letters).recognize(crop(), 16, 16)
        assertEquals(listOf<HayaiKvCache>(Cache1, Cache2), g.decodeCaches)
    }

    @Test
    fun textIsTheTokensBeforeTheEndMarker() {
        val g = ScriptedGraphs(4, listOf(5, 6, EOS), listOf(Cache1, Cache2, Cache3), VOCAB)
        assertEquals("abc", engine(g, letters).recognize(crop(), 16, 16))
    }

    @Test
    fun anImmediateEndMarkerYieldsEmptyTextAndNoDecodeStep() {
        val g = ScriptedGraphs(EOS, emptyList(), listOf(Cache1), VOCAB)
        assertEquals("", engine(g, letters).recognize(crop(), 16, 16))
        assertTrue(g.decodeTokens.isEmpty(), "no decode step should run")
    }

    @Test
    fun aPadFromPrefillAlsoEndsTheSequence() {
        val g = ScriptedGraphs(PAD, emptyList(), listOf(Cache1), VOCAB)
        assertEquals("", engine(g, letters).recognize(crop(), 16, 16))
    }

    @Test
    fun aPadFromADecodeStepEndsTheSequenceWithoutEmittingIt() {
        val g = ScriptedGraphs(4, listOf(5, PAD), listOf(Cache1, Cache2, Cache3), VOCAB)
        assertEquals("ab", engine(g, letters).recognize(crop(), 16, 16))
    }

    @Test
    fun maxNewTokensBoundsTheOutput() {
        val g = ScriptedGraphs(4, listOf(5, 6, 7, 4, 5), List(6) { Cache1 }, VOCAB)
        val text = engine(g, letters).recognize(crop(), 16, 16, maxNewTokens = 3)
        assertEquals("abc", text)
        assertEquals(2, g.decodeTokens.size, "3 tokens total means prefill plus two decode steps")
    }
}
