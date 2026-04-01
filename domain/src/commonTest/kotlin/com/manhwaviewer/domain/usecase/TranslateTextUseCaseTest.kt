package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.repository.TranslationRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslateTextUseCaseTest {

    @Test
    fun delegatesToRepository() = runTest {
        val repo = FakeTranslationRepository(mapOf("hello" to "안녕"))
        val useCase = TranslateTextUseCase(repo)
        val result = useCase(listOf("hello"))
        assertEquals(1, result.size)
        assertEquals("hello", result.first().originalText)
        assertEquals("안녕", result.first().translatedText)
    }

    @Test
    fun translatesMultipleTexts() = runTest {
        val repo = FakeTranslationRepository(mapOf("a" to "가", "b" to "나"))
        val useCase = TranslateTextUseCase(repo)
        val result = useCase(listOf("a", "b"))
        assertEquals(2, result.size)
        assertEquals("가", result[0].translatedText)
        assertEquals("나", result[1].translatedText)
    }

    @Test
    fun usesDefaultLanguageParameters() = runTest {
        val repo = FakeTranslationRepository()
        val useCase = TranslateTextUseCase(repo)
        useCase(listOf("test"))
        assertEquals("ja", repo.lastSourceLang)
        assertEquals("ko", repo.lastTargetLang)
    }
}

class FakeTranslationRepository(
    private val translations: Map<String, String> = emptyMap(),
) : TranslationRepository {
    var lastSourceLang: String? = null
    var lastTargetLang: String? = null

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String {
        lastSourceLang = sourceLanguage
        lastTargetLang = targetLanguage
        return translations[text] ?: "translated-$text"
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<TranslationResult> {
        lastSourceLang = sourceLanguage
        lastTargetLang = targetLanguage
        return texts.map { TranslationResult(it, translate(it, sourceLanguage, targetLanguage)) }
    }
}
