package com.manhwaviewer.data.translation

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FallbackTranslationServiceTest {

    private class Scripted(override val engineName: String, private val answer: (List<String>) -> List<String>) : TranslationService {
        var calls = 0
        override suspend fun translate(texts: List<String>, source: String, target: String): List<String> {
            calls++
            return answer(texts)
        }
    }

    private fun working(name: String) = Scripted(name) { texts -> texts.map { "$name:$it" } }
    private fun failing(name: String, error: Throwable) = Scripted(name) { throw error }

    @Test
    fun usesThePrimaryAndLeavesTheFallbackAlone() = runTest {
        val primary = working("PROXY")
        val fallback = working("GOOGLE_CLOUD")
        val service = FallbackTranslationService(primary, fallback)

        assertEquals(listOf("PROXY:あ"), service.translate(listOf("あ"), "ja", "ko"))
        assertEquals(0, fallback.calls)
        assertEquals("PROXY", service.engineName)
    }

    @Test
    fun fallsBackWhenThePrimaryFailsAndReportsWhoTranslated() = runTest {
        val service = FallbackTranslationService(failing("PROXY", RuntimeException("502")), working("GOOGLE_CLOUD"))

        assertEquals(listOf("GOOGLE_CLOUD:あ"), service.translate(listOf("あ"), "ja", "ko"))
        assertEquals("GOOGLE_CLOUD", service.engineName)
    }

    @Test
    fun goesBackToThePrimaryOnTheNextCall() = runTest {
        var broken = true
        val primary = Scripted("PROXY") { texts -> if (broken) throw RuntimeException("down") else texts.map { "PROXY:$it" } }
        val service = FallbackTranslationService(primary, working("GOOGLE_CLOUD"))

        service.translate(listOf("あ"), "ja", "ko")
        broken = false

        assertEquals(listOf("PROXY:い"), service.translate(listOf("い"), "ja", "ko"))
        assertEquals("PROXY", service.engineName)
    }

    @Test
    fun doesNotTurnACancellationIntoAFallbackCall() = runTest {
        val fallback = working("GOOGLE_CLOUD")
        val service = FallbackTranslationService(failing("PROXY", CancellationException("page changed")), fallback)

        assertFailsWith<CancellationException> { service.translate(listOf("あ"), "ja", "ko") }
        assertEquals(0, fallback.calls)
    }

    @Test
    fun failsWhenBothFail() = runTest {
        val service = FallbackTranslationService(failing("PROXY", RuntimeException("502")), failing("GOOGLE_CLOUD", RuntimeException("403")))

        assertFailsWith<RuntimeException> { service.translate(listOf("あ"), "ja", "ko") }
    }
}
