package com.manhwaviewer.data.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YoloPostProcessorTest {

    @Test
    fun parseAndFilter_filtersLowConfidence() {
        val raw = floatArrayOf(10f, 20f, 100f, 200f, 0.05f, 1f) +
            FloatArray((MangaOcrConfig.YOLO_MAX_DETECTIONS - 1) * 6)
        val result = YoloPostProcessor.parseAndFilter(raw, 1f, 0, 0, 640, 640)
        assertTrue(result.isEmpty(), "Low confidence detection should be filtered out")
    }

    /**
     * 0.15 는 정답셋에서 실제 말풍선들이 받은 점수 구간이다. 이 구간을 버리면 작은 말풍선과
     * 가늘고 긴 나레이션이 통째로 사라지므로, 임계값이 다시 올라가면 여기서 잡힌다.
     */
    @Test
    fun parseAndFilter_keepsWeakButRealDetections() {
        val raw = floatArrayOf(10f, 20f, 100f, 200f, 0.15f, 1f) +
            FloatArray((MangaOcrConfig.YOLO_MAX_DETECTIONS - 1) * 6)
        val result = YoloPostProcessor.parseAndFilter(raw, 1f, 0, 0, 640, 640)
        assertEquals(1, result.size, "0.15 detections must survive the confidence filter")
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
    fun addPadding_smallBoxUsesMinPadding() {
        // max(20, 30) * 0.08 = 2.4 -> 2 -> clamp 4
        val box = DetectedBox(x = 100, y = 100, width = 20, height = 30, confidence = 0.9f, isJapanese = true)
        val padded = YoloPostProcessor.addPadding(box, imageWidth = 1125, imageHeight = 1600)
        assertEquals(DetectedBox(x = 96, y = 96, width = 28, height = 38, confidence = 0.9f, isJapanese = true), padded)
    }

    @Test
    fun addPadding_mediumBoxUsesRatioPadding() {
        // max(100, 150) * 0.08 = 12
        val box = DetectedBox(x = 200, y = 300, width = 100, height = 150, confidence = 0.9f, isJapanese = true)
        val padded = YoloPostProcessor.addPadding(box, imageWidth = 1125, imageHeight = 1600)
        assertEquals(DetectedBox(x = 188, y = 288, width = 124, height = 174, confidence = 0.9f, isJapanese = true), padded)
    }

    @Test
    fun addPadding_largeBoxUsesMaxPadding() {
        // max(300, 500) * 0.08 = 40 -> clamp 20
        val box = DetectedBox(x = 200, y = 300, width = 300, height = 500, confidence = 0.9f, isJapanese = true)
        val padded = YoloPostProcessor.addPadding(box, imageWidth = 1125, imageHeight = 1600)
        assertEquals(DetectedBox(x = 180, y = 280, width = 340, height = 540, confidence = 0.9f, isJapanese = true), padded)
    }

    @Test
    fun addPadding_isClampedToImageEdges() {
        // pad 12: x=5-12 -> 0, w=100+24=124 but limited to 1125-0
        val box = DetectedBox(x = 5, y = 1500, width = 100, height = 150, confidence = 0.9f, isJapanese = true)
        val padded = YoloPostProcessor.addPadding(box, imageWidth = 1125, imageHeight = 1600)
        assertEquals(DetectedBox(x = 0, y = 1488, width = 124, height = 112, confidence = 0.9f, isJapanese = true), padded)
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
