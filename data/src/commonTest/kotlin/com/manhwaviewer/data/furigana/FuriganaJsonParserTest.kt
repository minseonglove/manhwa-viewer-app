package com.manhwaviewer.data.furigana

import com.manhwaviewer.domain.entity.FuriganaToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
        // 負け(まけ) → 負(ま) + け(け)로 분리되어 8개
        assertEquals(8, result.size)
        assertEquals("おれ", result[0].reading)        // 俺 → おれ
        assertEquals("ぜったい", result[2].reading)    // 絶対 → ぜったい
        assertEquals("負", result[4].surface)           // 負け → 負(ま) + け
        assertEquals("ま", result[4].reading)
        assertTrue(result[4].isKanji)
        assertEquals("け", result[5].surface)
        assertFalse(result[5].isKanji)
    }

    // ── 送り仮名 분리 테스트 ──

    @Test
    fun splitOkurigana_dasu() {
        // 出す(だす) → [出(だ), す(す)]
        val token = FuriganaToken("出す", "だす", isKanji = true)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(2, result.size)
        assertEquals("出", result[0].surface)
        assertEquals("だ", result[0].reading)
        assertTrue(result[0].isKanji)
        assertEquals("す", result[1].surface)
        assertEquals("す", result[1].reading)
        assertFalse(result[1].isKanji)
    }

    @Test
    fun splitOkurigana_taberu() {
        // 食べる(たべる) → [食(た), べる(べる)]
        val token = FuriganaToken("食べる", "たべる", isKanji = true)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(2, result.size)
        assertEquals("食", result[0].surface)
        assertEquals("た", result[0].reading)
        assertTrue(result[0].isKanji)
        assertEquals("べる", result[1].surface)
        assertFalse(result[1].isKanji)
    }

    @Test
    fun splitOkurigana_hashiru() {
        // 走る(はしる) → [走(はし), る(る)]
        val token = FuriganaToken("走る", "はしる", isKanji = true)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(2, result.size)
        assertEquals("走", result[0].surface)
        assertEquals("はし", result[0].reading)
        assertTrue(result[0].isKanji)
        assertEquals("る", result[1].surface)
        assertFalse(result[1].isKanji)
    }

    @Test
    fun splitOkurigana_otousan() {
        // お父さん(おとうさん) → [お(お), 父(とう), さん(さん)]
        val token = FuriganaToken("お父さん", "おとうさん", isKanji = true)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(3, result.size)
        assertEquals("お", result[0].surface)
        assertFalse(result[0].isKanji)
        assertEquals("父", result[1].surface)
        assertEquals("とう", result[1].reading)
        assertTrue(result[1].isKanji)
        assertEquals("さん", result[2].surface)
        assertFalse(result[2].isKanji)
    }

    @Test
    fun splitOkurigana_pureKanji_noSplit() {
        // 東京(とうきょう) → 분리 안함 (순수 한자)
        val token = FuriganaToken("東京", "とうきょう", isKanji = true)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(1, result.size)
        assertEquals("東京", result[0].surface)
        assertEquals("とうきょう", result[0].reading)
    }

    @Test
    fun splitOkurigana_hiragana_noSplit() {
        // は → isKanji=false이므로 분리 안함
        val token = FuriganaToken("は", "は", isKanji = false)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(1, result.size)
    }

    @Test
    fun splitOkurigana_appliedInParse() {
        // parse()에서 splitOkurigana가 적용되는지 확인
        val json = """[{"surface":"出す","reading":"だす","is_kanji":true}]"""
        val result = FuriganaJsonParser.parse(json)
        assertEquals(2, result.size)
        assertEquals("出", result[0].surface)
        assertEquals("だ", result[0].reading)
        assertTrue(result[0].isKanji)
        assertEquals("す", result[1].surface)
        assertFalse(result[1].isKanji)
    }

    @Test
    fun splitOkurigana_miseru() {
        // 見せる(みせる) → [見(み), せる(せる)]
        val token = FuriganaToken("見せる", "みせる", isKanji = true)
        val result = FuriganaJsonParser.splitOkurigana(token)
        assertEquals(2, result.size)
        assertEquals("見", result[0].surface)
        assertEquals("み", result[0].reading)
        assertTrue(result[0].isKanji)
        assertEquals("せる", result[1].surface)
        assertFalse(result[1].isKanji)
    }
}
