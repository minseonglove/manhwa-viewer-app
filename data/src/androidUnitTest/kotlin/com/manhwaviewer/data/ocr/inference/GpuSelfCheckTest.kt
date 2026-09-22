package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.DetectedBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpuSelfCheckTest {

    private fun box(x: Int, y: Int, w: Int = 40, h: Int = 120) = DetectedBox(x, y, w, h, confidence = 0.9f, isJapanese = true)

    @Test
    fun relativeErrorIsZeroForIdenticalOutputs() {
        val ref = floatArrayOf(1f, -2f, 3f, -4f)
        assertEquals(0f, GpuSelfCheck.relativeError(ref.copyOf(), ref))
    }

    @Test
    fun relativeErrorMeasuresTheMeanDeviationAgainstTheReferenceScale() {
        val ref = floatArrayOf(1f, -1f, 1f, -1f)
        val got = floatArrayOf(1.02f, -1.02f, 0.98f, -0.98f)
        assertEquals(0.02f, GpuSelfCheck.relativeError(got, ref), 1e-5f)
    }

    @Test
    fun anyNonFiniteValueFailsOutright() {
        val ref = floatArrayOf(1f, 1f, 1f)
        assertEquals(Float.POSITIVE_INFINITY, GpuSelfCheck.relativeError(floatArrayOf(1f, Float.NaN, 1f), ref))
        assertEquals(Float.POSITIVE_INFINITY, GpuSelfCheck.relativeError(floatArrayOf(Float.NEGATIVE_INFINITY, 1f, 1f), ref))
    }

    @Test
    fun boxesAgreeWhenEveryBoxHasAnOverlappingCounterpart() {
        val cpu = listOf(box(10, 10), box(300, 50))
        val gpu = listOf(box(302, 51), box(11, 9))
        assertTrue(GpuSelfCheck.boxesAgree(gpu, cpu))
    }

    @Test
    fun boxesDisagreeWhenTheCountsDiffer() {
        assertFalse(GpuSelfCheck.boxesAgree(listOf(box(10, 10)), listOf(box(10, 10), box(300, 50))))
    }

    @Test
    fun boxesDisagreeWhenABoxMovedAway() {
        assertFalse(GpuSelfCheck.boxesAgree(listOf(box(10, 10), box(200, 400)), listOf(box(10, 10), box(300, 50))))
    }

    @Test
    fun noBoxesOnEitherSideAgree() {
        assertTrue(GpuSelfCheck.boxesAgree(emptyList(), emptyList()))
    }
}
