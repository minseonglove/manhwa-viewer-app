package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Expected strings come from the reference tokenizer (`tokenizers`, the same `tokenizer.json`
 * shipped beside the graphs), decoding ids the model actually produced on the evaluation set.
 */
class HayaiTokenizerTest {

    /** The byte-level pieces the reference maps these ids to. */
    private val vocab = mapOf(
        111 to "¯",
        118 to "¶",
        263 to "ãģ«",
        285 to "âĢ¦",
        292 to "ãĤĭ",
        298 to "ãģĤ",
        424 to "ï½",
        673 to "ãĥī",
        1623 to "ãģĹãģĭ",
        1772 to "ãĤĦãģ¾",
        1952 to "ãĥ¼ãĥ³",
        2058 to "åĨĨ",
        2411 to "çĽ´",
        3210 to "100",
        3648 to "ç´ł",
        7810 to "ãģĤãģģ",
        2 to "[BOS]",
        3 to "[EOS]",
    )
    private val specials = setOf(0, 1, 2, 3)

    private fun tokenizer() = HayaiTokenizer(vocab, specials)

    @Test
    fun decode_matchesTheReferenceOnRealPredictions() {
        val cases = listOf(
            listOf(3648, 2411, 263, 298, 1772, 292, 1623) to "素直にあやまるしか",
            listOf(673, 1952) to "ドーン",
            listOf(7810, 285) to "あぁ…",
            listOf(3210, 2058) to "100円",
        )
        val tok = tokenizer()
        for ((ids, expected) in cases) {
            assertEquals(expected, tok.decode(ids), "ids=$ids")
        }
    }

    @Test
    fun decode_joinsAUtf8SequenceThatSpansTwoTokens() {
        // 424+118 is one half-width katakana and 424+111 the other: the byte run crosses the
        // token boundary, so decoding token-by-token and concatenating text would produce mojibake.
        assertEquals("ｶｯ", tokenizer().decode(listOf(424, 118, 424, 111)))
    }

    @Test
    fun decode_dropsSpecialTokensByDefault() {
        assertEquals("ドーン", tokenizer().decode(listOf(2, 673, 1952, 3)))
    }

    @Test
    fun decode_keepsSpecialTokensWhenAsked() {
        assertEquals(
            "[BOS]ドーン[EOS]",
            tokenizer().decode(listOf(2, 673, 1952, 3), skipSpecialTokens = false),
        )
    }

    @Test
    fun decode_ofNothingIsEmpty() {
        assertEquals("", tokenizer().decode(emptyList()))
        assertEquals("", tokenizer().decode(listOf(2, 3)))
    }

    @Test
    fun decode_refusesAnIdTheVocabDoesNotHave() {
        // Silently dropping it would turn a wrong argmax or a vocab/graph mismatch into
        // quietly truncated text instead of an error.
        val threw = try {
            tokenizer().decode(listOf(673, 999_999)); false
        } catch (e: IllegalArgumentException) {
            true
        }
        assertTrue(threw, "an id outside the vocab must be refused")
    }

    @Test
    fun fromTokenizerJson_readsTheVocabAndTheSpecialIds() {
        val json = """
            {
              "added_tokens": [
                {"id": 2, "content": "[BOS]", "special": true},
                {"id": 3, "content": "[EOS]", "special": true},
                {"id": 9, "content": "<ordinary>", "special": false}
              ],
              "model": {
                "type": "BPE",
                "vocab": {"[BOS]": 2, "[EOS]": 3, "100": 3210, "åĨĨ": 2058}
              }
            }
        """.trimIndent()
        val tok = HayaiTokenizer.fromTokenizerJson(json)
        assertEquals("100円", tok.decode(listOf(3210, 2058)))
        assertEquals("100円", tok.decode(listOf(2, 3210, 2058, 3)), "[BOS]/[EOS] are special")
    }

    @Test
    fun tokenIdOf_findsTheSpecialTokensTheLoopNeeds() {
        // The reference looks these up rather than hard-coding them; a hard-coded id that
        // drifted from the vocabulary would emit or swallow the wrong token silently.
        val tok = tokenizer()
        assertEquals(2, tok.tokenIdOf("[BOS]"))
        assertEquals(3, tok.tokenIdOf("[EOS]"))
        assertEquals(null, tok.tokenIdOf("<not-in-this-vocab>"))
    }

    @Test
    fun fromTokenizerJson_alsoCarriesAddedTokensThatAreNotInTheVocab() {
        // hayai's tokenizer.json puts <bos>/<eos>/<pad> (16000+) ONLY in added_tokens; reading
        // model.vocab alone leaves the loop unable to find the token it must start with.
        val json = """
            {
              "added_tokens": [
                {"id": 2, "content": "[BOS]", "special": true},
                {"id": 100, "content": "<bos>", "special": true},
                {"id": 101, "content": "<eos>", "special": true}
              ],
              "model": {"type": "BPE", "vocab": {"[BOS]": 2, "100": 3210}}
            }
        """.trimIndent()
        val tok = HayaiTokenizer.fromTokenizerJson(json)
        assertEquals(100, tok.tokenIdOf("<bos>"))
        assertEquals(101, tok.tokenIdOf("<eos>"))
        assertEquals(2, tok.tokenIdOf("[BOS]"))
    }

    @Test
    fun decode_skipsASpecialTokenBeforeRequiringItInTheVocab() {
        // A special id is dropped by its id, so it must not have to resolve to a piece first.
        val tok = HayaiTokenizer(mapOf(673 to "\u00E3\u0125\u012B", 1952 to "\u00E3\u0125\u00BC\u00E3\u0125\u00B3"), setOf(99))
        assertEquals("\u30C9\u30FC\u30F3", tok.decode(listOf(99, 673, 1952, 99)))
    }

    @Test
    fun fromTokenizerJson_refusesJsonWithoutAVocab() {
        val threw = try {
            HayaiTokenizer.fromTokenizerJson("""{"model":{"type":"BPE"}}"""); false
        } catch (e: IllegalArgumentException) {
            true
        }
        assertTrue(threw, "a tokenizer.json with no vocab must be refused, not silently empty")
    }
}
