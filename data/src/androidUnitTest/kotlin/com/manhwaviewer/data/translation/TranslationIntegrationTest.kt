package com.manhwaviewer.data.translation

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 실제 Papago API를 호출하는 통합 테스트.
 * local.properties에 papago.client.id / papago.client.secret 가 필요합니다.
 */
class TranslationIntegrationTest {

    private fun loadApiKeys(): Pair<String, String> {
        // Gradle 프로젝트 루트의 local.properties 탐색
        val candidates = listOf(
            File("local.properties"),
            File("../local.properties"),
            File("../../local.properties"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("local.properties not found — Papago API 키가 필요합니다")

        val lines = file.readLines()
        val clientId = lines.find { it.startsWith("papago.client.id=") }
            ?.substringAfter("=")?.trim()
            ?: error("papago.client.id not found in local.properties")
        val clientSecret = lines.find { it.startsWith("papago.client.secret=") }
            ?.substringAfter("=")?.trim()
            ?: error("papago.client.secret not found in local.properties")

        return clientId to clientSecret
    }

    private fun createRealRepo(): TranslationRepositoryImpl {
        val (clientId, clientSecret) = loadApiKeys()
        return TranslationRepositoryImpl(
            papagoService = PapagoTranslationService(clientId, clientSecret),
            googleService = GoogleTranslationService(),
            cacheDao = FakeTranslationCacheDao(),
        )
    }

    @Test
    fun translateSingleJapaneseText() = runTest {
        val (clientId, clientSecret) = loadApiKeys()
        val papago = PapagoTranslationService(clientId, clientSecret)

        // 직접 PapagoService를 호출하여 에러 확인
        val directResult = papago.translate("こんにちは", "ja", "ko")
        println("Papago 직접 호출: こんにちは -> $directResult")
        assertTrue(directResult.isNotBlank())
        assertNotEquals("こんにちは", directResult, "번역 결과가 원문과 동일합니다")

        // Repository를 통한 호출
        val repo = createRealRepo()
        val result = repo.translate("こんにちは")
        println("Repository 호출: こんにちは -> $result")
        assertNotEquals("こんにちは", result, "번역 결과가 원문과 동일합니다")
    }

    @Test
    fun translateBatchJapaneseTexts() = runTest {
        val repo = createRealRepo()
        val originals = listOf("おはようございます", "ありがとう", "さようなら")
        val results = repo.translateBatch(originals)

        assertEquals(3, results.size)

        results.forEachIndexed { i, tr ->
            println("번역 결과: ${tr.originalText} -> ${tr.translatedText}")
            assertEquals(originals[i], tr.originalText)
            assertTrue(tr.translatedText.isNotBlank())
            assertNotEquals(tr.originalText, tr.translatedText, "번역 결과가 원문과 동일합니다: ${tr.originalText}")
        }
    }

    @Test
    fun translateBatchMangaDialogue() = runTest {
        val repo = createRealRepo()
        val mangaTexts = listOf(
            "俺は海賊王になる",
            "お前はもう死んでいる",
            "逃げちゃダメだ",
        )
        val results = repo.translateBatch(mangaTexts)

        assertEquals(3, results.size)
        results.forEach { tr ->
            println("만화 번역: ${tr.originalText} -> ${tr.translatedText}")
            assertTrue(tr.translatedText.isNotBlank())
        }
    }

    @Test
    fun translateBatchCachesResults() = runTest {
        val repo = createRealRepo()

        // 첫 번째 호출: API 호출
        val first = repo.translateBatch(listOf("テスト"))
        val firstResult = first[0].translatedText

        // 두 번째 호출: 캐시 히트 (동일 결과)
        val second = repo.translateBatch(listOf("テスト"))
        assertEquals(firstResult, second[0].translatedText)

        println("캐시 테스트: テスト -> $firstResult (캐시 히트 확인)")
    }
}
