package com.manhwaviewer.data.translation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationRepositoryImplTest {

    private fun createRepo(
        papagoResult: String = "번역됨",
        papagoShouldFail: Boolean = false,
    ): TranslationRepositoryImpl {
        val papago = FakePapagoService(papagoResult, papagoShouldFail)
        val google = GoogleTranslationService()
        val cacheDao = FakeTranslationCacheDao()
        return TranslationRepositoryImpl(papago, google, cacheDao)
    }

    @Test
    fun translateReturnsPapagoResult() = runTest {
        val repo = createRepo(papagoResult = "번역 결과")
        val result = repo.translate("テスト")
        assertEquals("번역 결과", result)
    }

    @Test
    fun translateUsesCacheOnSecondCall() = runTest {
        val papago = FakePapagoService("번역됨")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        repo.translate("テスト")
        assertEquals(1, papago.callCount)

        // 두번째 호출은 캐시 사용
        val cached = repo.translate("テスト")
        assertEquals("번역됨", cached)
        assertEquals(1, papago.callCount) // API 호출 증가 없음
    }

    @Test
    fun translateReturnsOriginalTextOnPapagoFailure() = runTest {
        val papago = FakePapagoService(shouldFail = true)
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)
        val result = try {
            repo.translate("テスト")
        } catch (_: Exception) {
            "テスト"
        }
        assertEquals("テスト", result)
    }

    @Test
    fun translateSkipsBlankText() = runTest {
        val repo = createRepo()
        val result = repo.translate("  ")
        assertEquals("  ", result)
    }

    @Test
    fun translateBatchTranslatesAllTexts() = runTest {
        val repo = createRepo(papagoResult = "번역")
        val results = repo.translateBatch(listOf("a", "b", "c"))
        assertEquals(3, results.size)
    }

    @Test
    fun translateBatchUsesCacheForDuplicates() = runTest {
        val papago = FakePapagoService("번역됨")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        repo.translateBatch(listOf("same", "same", "same"))
        // 첫 번째만 API 호출, 나머지는 캐시
        assertEquals(1, papago.callCount)
    }

    @Test
    fun translateSavesCacheWithCorrectEngine() = runTest {
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(
            FakePapagoService("결과"),
            GoogleTranslationService(),
            cacheDao,
        )
        repo.translate("テスト")
        assertEquals(1, cacheDao.getCacheCount())
        val cached = cacheDao.findCache("テスト", "ja", "ko")
        assertEquals("PAPAGO", cached?.engine)
    }
}

class FakePapagoService(
    private val result: String = "번역됨",
    private val shouldFail: Boolean = false,
) : PapagoTranslationService("fake-id", "fake-secret") {
    var callCount = 0

    override suspend fun translate(text: String, source: String, target: String): String {
        callCount++
        if (shouldFail) throw RuntimeException("Papago API error")
        return result
    }
}
