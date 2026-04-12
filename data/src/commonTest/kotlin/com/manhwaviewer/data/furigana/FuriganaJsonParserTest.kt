package com.manhwaviewer.data.furigana

import com.manhwaviewer.domain.entity.FuriganaToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuriganaJsonParserTest {

    @Test
    fun parseSingleKanjiToken() {
        val json = """[{"surface":"今日","reading":"きょう","is_kanji":true}]"""
        val result = FuriganaJsonParser.parse(json)
        assertEquals(1, result.size)
        assertEquals("今日", result[0].surface)
        assertEquals("きょう", result[0].reading)
        assertTrue(result[0].isKanji)
    }

    @Test
    fun parseHiraganaToken() {
        val json = """[{"surface":"は","reading":"は","is_kanji":false}]"""
        val result = FuriganaJsonParser.parse(json)
        assertEquals(1, result.size)
        assertEquals("は", result[0].surface)
        assertEquals("は", result[0].reading)
        assertTrue(!result[0].isKanji)
    }

    @Test
    fun parseMultipleTokens() {
        val json = """[
            {"surface":"今日","reading":"きょう","is_kanji":true},
            {"surface":"は","reading":"は","is_kanji":false},
            {"surface":"天気","reading":"てんき","is_kanji":true}
        ]"""
        val result = FuriganaJsonParser.parse(json)
        assertEquals(3, result.size)
        assertEquals("今日", result[0].surface)
        assertEquals("は", result[1].surface)
        assertEquals("天気", result[2].surface)
    }

    @Test
    fun parseEmptyArray() {
        val json = "[]"
        val result = FuriganaJsonParser.parse(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseInvalidJsonReturnsEmpty() {
        val result = FuriganaJsonParser.parse("invalid json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseNullStringReturnsEmpty() {
        val result = FuriganaJsonParser.parse("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseMangaDialogueTokens() {
        val json = """[
            {"surface":"俺","reading":"おれ","is_kanji":true},
            {"surface":"は","reading":"は","is_kanji":false},
            {"surface":"絶対","reading":"ぜったい","is_kanji":true},
            {"surface":"に","reading":"に","is_kanji":false},
            {"surface":"負け","reading":"まけ","is_kanji":true},
            {"surface":"ない","reading":"ない","is_kanji":false},
            {"surface":"！","reading":"！","is_kanji":false}
        ]"""
        val result = FuriganaJsonParser.parse(json)
        assertEquals(7, result.size)
        assertEquals("おれ", result[0].reading)
        assertEquals("ぜったい", result[2].reading)
        assertEquals("まけ", result[4].reading)
    }
}
