package com.manhwaviewer.data.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

class MangaOcrTokenizerTest {

    private val sampleVocab = buildList {
        add("[PAD]")   // 0
        add("[UNK]")   // 1
        add("[CLS]")   // 2
        add("[SEP]")   // 3
        add("[MASK]")  // 4
        add("素")      // 5
        add("直")      // 6
        add("に")      // 7
        add("あ")      // 8
        add("や")      // 9
        add("ま")      // 10
        add("る")      // 11
        add("し")      // 12
        add("か")      // 13
        add("##直")    // 14
    }

    private val tokenizer = MangaOcrTokenizer(sampleVocab)

    @Test
    fun decodesTokenIdsToText() {
        val tokenIds = listOf(2, 5, 6, 7, 8, 9, 10, 11, 12, 13, 3)
        val result = tokenizer.decode(tokenIds)
        assertEquals("素直にあやまるしか", result)
    }

    @Test
    fun skipsSpecialTokens() {
        val tokenIds = listOf(0, 2, 5, 6, 3, 4)
        val result = tokenizer.decode(tokenIds)
        assertEquals("素直", result)
    }

    @Test
    fun handlesWordPieceContinuationTokens() {
        val tokenIds = listOf(2, 5, 14, 3)
        val result = tokenizer.decode(tokenIds)
        assertEquals("素直", result)
    }

    @Test
    fun returnsEmptyStringForOnlySpecialTokens() {
        val tokenIds = listOf(2, 3)
        val result = tokenizer.decode(tokenIds)
        assertEquals("", result)
    }

    @Test
    fun returnsEmptyStringForEmptyInput() {
        val result = tokenizer.decode(emptyList())
        assertEquals("", result)
    }

    @Test
    fun ignoresOutOfRangeTokenIds() {
        val tokenIds = listOf(2, 5, 9999, 6, 3)
        val result = tokenizer.decode(tokenIds)
        assertEquals("素直", result)
    }
}
