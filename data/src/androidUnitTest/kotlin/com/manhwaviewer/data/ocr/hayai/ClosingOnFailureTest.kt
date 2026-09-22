package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ClosingOnFailureTest {

    private val closed = mutableListOf<String>()

    @Test
    fun closesNothingWhenTheBuildSucceeds() {
        val built = closingOnFailure({ closed += "encoder" }, { closed += "decoder" }) { "engine" }

        assertEquals("engine", built)
        assertEquals(emptyList(), closed, "what the engine now owns must stay open")
    }

    @Test
    fun closesEverythingOpenedWhenTheBuildFails() {
        val failure = IllegalStateException("hayai_prefill.onnx is missing")

        val thrown = assertFailsWith<IllegalStateException> {
            closingOnFailure({ closed += "encoder" }, { closed += "decoder" }) { throw failure }
        }

        assertSame(failure, thrown)
        assertEquals(listOf("encoder", "decoder"), closed)
    }

    @Test
    fun aCloseThatFailsDoesNotHideTheFailureOrSkipTheRest() {
        val failure = IllegalStateException("tokenizer.json carries no <bos>")
        val closeFailure = IllegalStateException("already closed")

        val thrown = assertFailsWith<IllegalStateException> {
            closingOnFailure({ throw closeFailure }, { closed += "decoder" }) { throw failure }
        }

        assertSame(failure, thrown)
        assertEquals(listOf("decoder"), closed)
        assertSame(closeFailure, thrown.suppressed.single())
    }
}
