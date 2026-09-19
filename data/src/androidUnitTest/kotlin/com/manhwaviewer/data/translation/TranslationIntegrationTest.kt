package com.manhwaviewer.data.translation

import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 실제 Google Cloud Translation API 를 호출하는 통합 테스트.
 * local.properties 에 google.translate.api.key 가 없으면 건너뛴다.
 */
class TranslationIntegrationTest {

    private fun loadApiKey(): String? {
        val candidates = listOf(
            File("local.properties"),
            File("../local.properties"),
            File("../../local.properties"),
        )
        return candidates.firstOrNull { it.exists() }
            ?.readLines()
            ?.find { it.startsWith("google.translate.api.key=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun createRealRepo(): TranslationRepositoryImpl {
        val apiKey = loadApiKey()
        assumeTrue("google.translate.api.key 가 없어 건너뜀", apiKey != null)
        return TranslationRepositoryImpl(
            translationService = GoogleTranslationService(apiKey!!),
            cacheDao = FakeTranslationCacheDao(),
        )
    }

    @Test
    fun translateSingleJapaneseText() = runTest {
        val repo = createRealRepo()

        val result = repo.translate("こんにちは")

        println("번역 결과: こんにちは -> $result")
        assertNotEquals("こんにちは", result, "번역 결과가 원문과 동일합니다")
    }

    @Test
    fun translateBatchKeepsOrder() = runTest {
        val repo = createRealRepo()
        val originals = listOf("おはようございます", "ありがとう", "さようなら")

        val results = repo.translateBatch(originals)

        assertEquals(originals, results.map { it.originalText })
        results.forEach { tr ->
            println("번역 결과: ${tr.originalText} -> ${tr.translatedText}")
            assertTrue(tr.translatedText.isNotBlank())
            assertNotEquals(tr.originalText, tr.translatedText, "번역 결과가 원문과 동일합니다: ${tr.originalText}")
        }
    }

    @Test
    fun multiLineTextComesBackAsOneItemWithoutHtmlEntities() = runTest {
        val repo = createRealRepo()

        val results = repo.translateBatch(listOf("今日は「晴れ」です\n明日は雨でしょう", "はい"))

        assertEquals(2, results.size)
        results.forEach { println("번역 결과: ${it.originalText} -> ${it.translatedText}") }
        assertTrue("&#" !in results[0].translatedText && "&quot;" !in results[0].translatedText)
    }

    @Test
    fun secondCallIsServedFromCache() = runTest {
        val repo = createRealRepo()

        val first = repo.translateBatch(listOf("テスト"))
        val second = repo.translateBatch(listOf("テスト"))

        assertEquals(first[0].translatedText, second[0].translatedText)
    }
}
