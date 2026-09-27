package com.manhwaviewer.data.translation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationRepositoryImplTest {

    @Test
    fun translateReturnsServiceResult() = runTest {
        val repo = TranslationRepositoryImpl(FakeTranslationService(), FakeTranslationCacheDao())

        assertEquals("번역:テスト", repo.translate("テスト"))
    }

    @Test
    fun translateUsesCacheOnSecondCall() = runTest {
        val service = FakeTranslationService()
        val repo = TranslationRepositoryImpl(service, FakeTranslationCacheDao())

        repo.translate("テスト")
        val cached = repo.translate("テスト")

        assertEquals("번역:テスト", cached)
        assertEquals(listOf(listOf("テスト")), service.requests)
    }

    @Test
    fun translateFailsWhenTheServiceFails() = runTest {
        val repo = TranslationRepositoryImpl(FakeTranslationService(shouldFail = true), FakeTranslationCacheDao())

        assertFailsWith<RuntimeException> { repo.translate("テスト") }
        assertFailsWith<RuntimeException> { repo.translateBatch(listOf("テスト", "テスト2"), "ja", "ko") }
    }

    @Test
    fun failedTranslationIsNotCached() = runTest {
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(FakeTranslationService(shouldFail = true), cacheDao)

        assertFailsWith<RuntimeException> { repo.translate("テスト") }

        assertEquals(0, cacheDao.entries.size)
    }

    @Test
    fun translateSkipsBlankText() = runTest {
        val service = FakeTranslationService()
        val repo = TranslationRepositoryImpl(service, FakeTranslationCacheDao())

        assertEquals("  ", repo.translate("  "))
        assertEquals(emptyList(), service.requests)
    }

    @Test
    fun translateBatchKeepsResultsAlignedWithInputs() = runTest {
        val repo = TranslationRepositoryImpl(FakeTranslationService(), FakeTranslationCacheDao())

        val results = repo.translateBatch(listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), results.map { it.originalText })
        assertEquals(listOf("번역:a", "번역:b", "번역:c"), results.map { it.translatedText })
    }

    @Test
    fun translateBatchSendsUncachedTextsInSingleCall() = runTest {
        val service = FakeTranslationService()
        val repo = TranslationRepositoryImpl(service, FakeTranslationCacheDao())

        repo.translateBatch(listOf("a", "b", "c"))

        assertEquals(listOf(listOf("a", "b", "c")), service.requests)
    }

    @Test
    fun multiLineTextIsTranslatedAsOneItem() = runTest {
        val service = FakeTranslationService()
        val repo = TranslationRepositoryImpl(service, FakeTranslationCacheDao())

        val results = repo.translateBatch(listOf("一行目\n二行目", "b"))

        assertEquals(listOf(listOf("一行目\n二行目", "b")), service.requests)
        assertEquals("번역:一行目\n二行目", results[0].translatedText)
        assertEquals("번역:b", results[1].translatedText)
    }

    @Test
    fun translateBatchRequestsOnlyUncachedTexts() = runTest {
        val service = FakeTranslationService()
        val repo = TranslationRepositoryImpl(service, FakeTranslationCacheDao())
        repo.translateBatch(listOf("a"))

        val results = repo.translateBatch(listOf("a", "b", "c"))

        assertEquals(listOf(listOf("a"), listOf("b", "c")), service.requests)
        assertEquals(listOf("번역:a", "번역:b", "번역:c"), results.map { it.translatedText })
    }

    @Test
    fun cachedTranslationSurvivesServiceChange() = runTest {
        val cacheDao = FakeTranslationCacheDao()
        TranslationRepositoryImpl(FakeTranslationService { "이전:$it" }, cacheDao).translateBatch(listOf("a"))
        val service = FakeTranslationService { "새:$it" }
        val repo = TranslationRepositoryImpl(service, cacheDao)

        val results = repo.translateBatch(listOf("a", "c"))

        assertEquals(listOf("이전:a", "새:c"), results.map { it.translatedText })
        assertEquals(listOf(listOf("c")), service.requests)
    }

    @Test
    fun translateSavesCacheWithServiceEngineName() = runTest {
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(FakeTranslationService(), cacheDao)

        repo.translate("テスト")

        assertEquals("FAKE", cacheDao.entries.single().engine)
    }
}

class FakeTranslationService(
    private val shouldFail: Boolean = false,
    private val translateText: (String) -> String = { "번역:$it" },
) : TranslationService {
    override val engineName = "FAKE"
    val requests = mutableListOf<List<String>>()

    override suspend fun translate(texts: List<String>, source: String, target: String): List<String> {
        requests += texts
        if (shouldFail) throw RuntimeException("translation failed")
        return texts.map(translateText)
    }
}
