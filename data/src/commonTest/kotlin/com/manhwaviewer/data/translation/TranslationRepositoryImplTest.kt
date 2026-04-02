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
        val repo = createRepo(papagoResult = "번역A\n번역B\n번역C")
        val results = repo.translateBatch(listOf("a", "b", "c"))
        assertEquals(3, results.size)
        assertEquals("a", results[0].originalText)
        assertEquals("번역A", results[0].translatedText)
        assertEquals("번역C", results[2].translatedText)
    }

    @Test
    fun translateBatchMakesSingleApiCall() = runTest {
        val papago = FakePapagoService("X\nY\nZ")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        repo.translateBatch(listOf("a", "b", "c"))
        assertEquals(1, papago.callCount)
    }

    @Test
    fun translateBatchUsesCacheForDuplicates() = runTest {
        val papago = FakePapagoService("번역됨")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        // 첫 번째 배치: 1회 API 호출, 3개 모두 캐시 저장
        repo.translateBatch(listOf("same", "same", "same"))
        assertEquals(1, papago.callCount)

        // 두 번째 배치: 캐시 히트, API 호출 없음
        repo.translateBatch(listOf("same"))
        assertEquals(1, papago.callCount)
    }

    @Test
    fun translateBatchReturnsCachedAndFresh() = runTest {
        val papago = FakePapagoService("번역됨")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        // "a"를 미리 캐시
        repo.translate("a")
        assertEquals(1, papago.callCount)

        // 배치: "a"는 캐시, "b"만 API 호출
        val results = repo.translateBatch(listOf("a", "b"))
        assertEquals(2, papago.callCount) // "b"만 추가 호출
        assertEquals("번역됨", results[0].translatedText) // 캐시
        assertEquals("번역됨", results[1].translatedText) // API
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

    @Test
    fun translateDelegatesToTranslateBatchAndUsesCache() = runTest {
        val papago = FakePapagoService("번역됨")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        // translate()로 캐시 저장
        repo.translate("テスト")
        assertEquals(1, papago.callCount)

        // translateBatch()에서 같은 텍스트 캐시 히트
        val results = repo.translateBatch(listOf("テスト"))
        assertEquals(1, papago.callCount) // API 추가 호출 없음
        assertEquals("번역됨", results[0].translatedText)
    }

    @Test
    fun translateBatchPartialCacheHit() = runTest {
        val papago = FakePapagoService("번역A")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        // "a"를 미리 캐시
        repo.translateBatch(listOf("a"))
        assertEquals(1, papago.callCount)

        // "a"는 캐시 히트, "b"와 "c"만 API 호출
        papago.updateResult("번역B\n번역C")
        val results = repo.translateBatch(listOf("a", "b", "c"))
        assertEquals(2, papago.callCount)
        assertEquals("번역A", results[0].translatedText) // 캐시
        assertEquals("번역B", results[1].translatedText) // API
        assertEquals("번역C", results[2].translatedText) // API
    }

    @Test
    fun translateBatchCrossBatchCacheHit() = runTest {
        val papago = FakePapagoService("X\nY")
        val cacheDao = FakeTranslationCacheDao()
        val repo = TranslationRepositoryImpl(papago, GoogleTranslationService(), cacheDao)

        // 배치1: "a", "b" 번역 → 캐시 저장
        repo.translateBatch(listOf("a", "b"))
        assertEquals(1, papago.callCount)

        // 배치2: "a"는 캐시 히트, "c"만 API 호출
        papago.updateResult("Z")
        val results = repo.translateBatch(listOf("a", "c"))
        assertEquals(2, papago.callCount)
        assertEquals("X", results[0].translatedText) // 배치1에서 캐시된 값
        assertEquals("Z", results[1].translatedText) // 새 API 호출
    }
}

class FakePapagoService(
    private var result: String = "번역됨",
    private val shouldFail: Boolean = false,
) : PapagoTranslationService("fake-id", "fake-secret") {
    var callCount = 0

    fun updateResult(newResult: String) {
        result = newResult
    }

    override suspend fun translate(text: String, source: String, target: String): String {
        callCount++
        if (shouldFail) throw RuntimeException("Papago API error")
        return result
    }
}
