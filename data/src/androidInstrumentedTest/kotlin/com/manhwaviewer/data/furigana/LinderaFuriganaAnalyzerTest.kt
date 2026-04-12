package com.manhwaviewer.data.furigana

import android.util.Log
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

/**
 * Lindera 네이티브 라이브러리를 실제 Android 기기에서 로드하여
 * 일본어 텍스트의 후리가나 변환을 검증하는 통합 테스트.
 */
class LinderaFuriganaAnalyzerTest {

    companion object {
        private const val TAG = "FuriganaTest"
    }

    private val analyzer = LinderaFuriganaAnalyzer()

    // ── 초기화 ──

    @Test
    fun initializesSuccessfully() = runTest {
        // analyze가 비어있지 않은 결과를 반환하면 초기화 성공
        val tokens = analyzer.analyze("テスト")
        Log.d(TAG, "init: tokens=${tokens.size}")
        assertTrue("Lindera should initialize and tokenize", tokens.isNotEmpty())
    }

    // ── 합성어 정확도 ──

    @Test
    fun compoundWord_kyou() = runTest {
        val tokens = analyzer.analyze("今日")
        Log.d(TAG, "今日: $tokens")
        val kanjiTokens = tokens.filter { it.isKanji }
        assertTrue("今日 should have kanji tokens", kanjiTokens.isNotEmpty())
        assertEquals("きょう", kanjiTokens[0].reading)
    }

    @Test
    fun compoundWord_taberu() = runTest {
        val tokens = analyzer.analyze("食べる")
        Log.d(TAG, "食べる: $tokens")
        val surfaces = tokens.joinToString("") { it.surface }
        assertEquals("食べる", surfaces)
        assertTrue("食 should be kanji", tokens[0].isKanji)
    }

    @Test
    fun compoundWord_zettai() = runTest {
        val tokens = analyzer.analyze("絶対")
        Log.d(TAG, "絶対: $tokens")
        val kanjiTokens = tokens.filter { it.isKanji }
        assertTrue("絶対 should be kanji", kanjiTokens.isNotEmpty())
        assertEquals("ぜったい", kanjiTokens[0].reading)
    }

    // ── 비한자 판별 ──

    @Test
    fun pureHiragana_notKanji() = runTest {
        val tokens = analyzer.analyze("おはよう")
        Log.d(TAG, "おはよう: $tokens")
        tokens.forEach { token ->
            assertFalse("Hiragana '${token.surface}' should not be kanji", token.isKanji)
        }
    }

    @Test
    fun katakana_notKanji() = runTest {
        val tokens = analyzer.analyze("タワー")
        Log.d(TAG, "タワー: $tokens")
        tokens.forEach { token ->
            assertFalse("Katakana '${token.surface}' should not be kanji", token.isKanji)
        }
    }

    // ── 혼합 문장 ──

    @Test
    fun mixedSentence_surfacesConcat() = runTest {
        val text = "今日は天気がいいですね"
        val tokens = analyzer.analyze(text)
        Log.d(TAG, "$text: $tokens")
        val concat = tokens.joinToString("") { it.surface }
        assertEquals(text, concat)
    }

    @Test
    fun mixedSentence_kanjiVsHiragana() = runTest {
        val tokens = analyzer.analyze("今日は天気がいいですね")
        val kyou = tokens.find { it.surface == "今日" }
        val ha = tokens.find { it.surface == "は" }
        assertTrue("今日 should be kanji", kyou?.isKanji == true)
        assertFalse("は should not be kanji", ha?.isKanji == true)
    }

    // ── 만화 대사 ──

    @Test
    fun mangaDialogue() = runTest {
        val text = "俺は絶対に負けない！"
        val tokens = analyzer.analyze(text)
        Log.d(TAG, "$text: $tokens")
        val concat = tokens.joinToString("") { it.surface }
        assertEquals(text, concat)
        val ore = tokens.find { it.surface == "俺" }
        assertTrue("俺 should be kanji", ore?.isKanji == true)
    }

    // ── 엣지 케이스 ──

    @Test
    fun emptyString_returnsEmpty() = runTest {
        val tokens = analyzer.analyze("")
        assertTrue("Empty string should return empty list", tokens.isEmpty())
    }

    @Test
    fun blankString_returnsEmpty() = runTest {
        val tokens = analyzer.analyze("   ")
        assertTrue("Blank string should return empty list", tokens.isEmpty())
    }

    @Test
    fun numbersAndKanji() = runTest {
        val tokens = analyzer.analyze("3人")
        Log.d(TAG, "3人: $tokens")
        val jin = tokens.find { it.surface == "人" }
        assertTrue("人 should be kanji", jin?.isKanji == true)
    }

    // ── 읽기(reading) 검증 ──

    @Test
    fun reading_isHiragana() = runTest {
        val tokens = analyzer.analyze("東京")
        Log.d(TAG, "東京: $tokens")
        val kanjiTokens = tokens.filter { it.isKanji }
        assertTrue("東京 should have kanji tokens", kanjiTokens.isNotEmpty())
        val reading = kanjiTokens[0].reading
        // 히라가나 범위: U+3040-U+309F (장음 ー 허용)
        assertTrue(
            "Reading '$reading' should be hiragana",
            reading.all { c ->
                c in '\u3040'..'\u309F' || c in '\u30A0'..'\u30FF' || c == 'ー'
            }
        )
    }

    // ── 성능 ──

    @Test
    fun performance_underThreshold() = runTest {
        // 첫 호출 (웜업)
        analyzer.analyze("ウォームアップ")

        val text = "俺は絶対に負けない！今日は天気がいいですね。"
        val start = System.currentTimeMillis()
        repeat(10) { analyzer.analyze(text) }
        val elapsed = System.currentTimeMillis() - start
        val avgMs = elapsed / 10.0
        Log.d(TAG, "Performance: ${avgMs}ms avg per call (10 runs)")
        assertTrue("Avg tokenization should be under 100ms, was ${avgMs}ms", avgMs < 100)
    }
}
