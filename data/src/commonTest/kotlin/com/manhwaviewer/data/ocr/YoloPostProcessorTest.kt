package com.manhwaviewer.data.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YoloPostProcessorTest {

    @Test
    fun parseAndFilter_filtersLowConfidence() {
        val raw = floatArrayOf(10f, 20f, 100f, 200f, 0.1f, 1f) +
            FloatArray((MangaOcrConfig.YOLO_MAX_DETECTIONS - 1) * 6)
        val result = YoloPostProcessor.parseAndFilter(raw, 1f, 0, 0, 640, 640)
        assertTrue(result.isEmpty(), "Low confidence detection should be filtered out")
    }

    @Test
    fun parseAndFilter_filtersNonTextClass() {
        val raw = floatArrayOf(10f, 20f, 100f, 200f, 0.9f, 0f) +
            FloatArray((MangaOcrConfig.YOLO_MAX_DETECTIONS - 1) * 6)
        val result = YoloPostProcessor.parseAndFilter(raw, 1f, 0, 0, 640, 640)
        assertTrue(result.isEmpty(), "Non-text class should be filtered out")
    }

    @Test
    fun parseAndFilter_validDetection() {
        val raw = floatArrayOf(100f, 50f, 200f, 150f, 0.8f, 1f) +
            FloatArray((MangaOcrConfig.YOLO_MAX_DETECTIONS - 1) * 6)
        val result = YoloPostProcessor.parseAndFilter(raw, 0.5f, 10, 20, 1000, 1000)
        assertEquals(1, result.size)
        val box = result[0]
        assertEquals(180, box.x)
        assertEquals(60, box.y)
        assertEquals(200, box.width)
        assertEquals(200, box.height)
    }

    @Test
    fun addPadding_respectsBounds() {
        val box = DetectedBox(x = 5, y = 5, width = 100, height = 200, confidence = 0.9f, isJapanese = true)
        val padded = YoloPostProcessor.addPadding(box, imageWidth = 640, imageHeight = 480)
        assertTrue(padded.x >= 0)
        assertTrue(padded.y >= 0)
        assertTrue(padded.x + padded.width <= 640)
        assertTrue(padded.y + padded.height <= 480)
    }

    @Test
    fun computeTransform_landscape() {
        val t = YoloPostProcessor.computeTransform(1280, 720, 640)
        assertEquals(0.5f, t.scale)
        assertEquals(0, t.padX)
        assertEquals(140, t.padY)
    }

    @Test
    fun nonMaxSuppression_removesOverlapping() {
        val boxes = listOf(
            DetectedBox(10, 10, 100, 100, 0.9f, true),
            DetectedBox(15, 15, 100, 100, 0.7f, true),  // overlaps with first
            DetectedBox(300, 300, 50, 50, 0.8f, true),   // no overlap
        )
        val result = YoloPostProcessor.nonMaxSuppression(boxes)
        assertEquals(2, result.size)
        assertEquals(0.9f, result[0].confidence)
        assertEquals(0.8f, result[1].confidence)
    }
}
