package com.manhwaviewer.data.ocr.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AcceleratorFallbackTest {

    private class Model(val name: String) {
        var released = false
    }

    private val gpuModel = Model("gpu")
    private val cpuModel = Model("cpu")
    private var cpuOpened = 0

    private class History(var skip: String? = null) : GpuHistory {
        val events = mutableListOf<String>()
        override fun skipReason() = skip
        override fun attemptStarted() { events += "started" }
        override fun attemptReturned() { events += "returned" }
        override fun rejected() { events += "rejected" }
    }

    private fun open(
        preferGpu: Boolean = true,
        gpu: () -> Model = { gpuModel },
        agrees: (Model) -> Boolean = { true },
        history: GpuHistory? = null,
    ) = AcceleratorFallback.open(
        preferGpu = preferGpu,
        gpu = gpu,
        cpu = { cpuOpened++; cpuModel },
        agrees = agrees,
        release = { it.released = true },
        history = history,
    )

    @Test
    fun usesTheGpuWhenItCompilesAndAgrees() {
        val opened = open()

        assertSame(gpuModel, opened.model)
        assertEquals(OcrAccelerator.GPU, opened.accelerator)
        assertNull(opened.gpuFailure)
        assertEquals(0, cpuOpened, "the CPU model is not opened when the GPU one is kept")
    }

    @Test
    fun fallsBackToTheCpuWhenTheGpuDoesNotCompile() {
        val opened = open(gpu = { error("Failed to compile model") })

        assertSame(cpuModel, opened.model)
        assertEquals(OcrAccelerator.CPU, opened.accelerator)
        assertTrue(opened.gpuFailure!!.contains("Failed to compile model"))
    }

    @Test
    fun fallsBackWhenANativeLibraryFailsToLink() {
        val opened = open(gpu = { throw UnsatisfiedLinkError("libLiteRtClGlAccelerator.so not found") })

        assertSame(cpuModel, opened.model)
        assertTrue(opened.gpuFailure!!.contains("libLiteRtClGlAccelerator"))
    }

    @Test
    fun releasesTheGpuModelAndFallsBackWhenItsOutputDisagrees() {
        val opened = open(agrees = { false })

        assertSame(cpuModel, opened.model)
        assertTrue(gpuModel.released, "a GPU model that computes wrong answers must not stay loaded")
        assertNotNull(opened.gpuFailure)
    }

    @Test
    fun fallsBackWhenTheCheckItselfThrows() {
        val opened = open(agrees = { error("readFloat failed") })

        assertSame(cpuModel, opened.model)
        assertTrue(gpuModel.released)
        assertTrue(opened.gpuFailure!!.contains("readFloat failed"))
    }

    @Test
    fun releasesTheGpuModelEvenWhenAnErrorEscapesTheCheck() {
        assertFailsWith<OutOfMemoryError> { open(agrees = { throw OutOfMemoryError() }) }
        assertTrue(gpuModel.released)
    }

    @Test
    fun goesStraightToTheCpuWhenTheGpuIsNotWanted() {
        var gpuTried = false
        val opened = open(preferGpu = false, gpu = { gpuTried = true; gpuModel })

        assertSame(cpuModel, opened.model)
        assertEquals(false, gpuTried)
        assertNull(opened.gpuFailure)
    }

    @Test
    fun skipsTheGpuWhenTheHistoryAdvisesAgainstIt() {
        var gpuTried = false
        val history = History(skip = "2 earlier GPU attempts never returned")
        val opened = open(gpu = { gpuTried = true; gpuModel }, history = history)

        assertSame(cpuModel, opened.model)
        assertEquals(false, gpuTried)
        assertEquals("2 earlier GPU attempts never returned", opened.gpuFailure)
        assertEquals(emptyList(), history.events)
    }

    @Test
    fun bracketsAKeptAttempt() {
        val history = History()
        open(history = history)
        assertEquals(listOf("started", "returned"), history.events)
    }

    @Test
    fun recordsARejectionAfterTheAttemptReturns() {
        val history = History()
        open(gpu = { error("Failed to compile model") }, history = history)
        assertEquals(listOf("started", "returned", "rejected"), history.events, "a thrown exception is a clean failure, not a crash")

        val disagreeing = History()
        open(agrees = { false }, history = disagreeing)
        assertEquals(listOf("started", "returned", "rejected"), disagreeing.events)
    }
}
