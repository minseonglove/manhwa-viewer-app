package com.manhwaviewer.data.ocr

import kotlin.math.roundToInt

object YoloPostProcessor {

    /**
     * 정답셋 30페이지에서 말풍선·나레이션이 받은 점수는 0.9 이상에 몰려 있고, 정답과 맞지 않는
     * 제안은 사실상 전부 0.07 이하다. 그 사이의 빈 구간 위에 놓는 값 — 작은(3,000px² 미만)
     * 말풍선과 가늘고 긴 나레이션이 이 구간에 떨어지고, 더 내리면 잡히는 것보다 쓰레기가 빨리 는다.
     */
    private const val CONF_THRESHOLD = 0.1f
    private const val TEXT_CLASS_ID = 1f
    private const val PADDING_RATIO = 0.08f
    private const val MIN_PADDING_PX = 4
    private const val MAX_PADDING_PX = 20
    private const val MIN_BOX_SIZE_PX = 8

    fun parseAndFilter(
        rawOutput: FloatArray,
        scale: Float,
        padX: Int,
        padY: Int,
        originalWidth: Int,
        originalHeight: Int,
    ): List<DetectedBox> {
        val boxes = mutableListOf<DetectedBox>()
        for (i in 0 until MangaOcrConfig.YOLO_MAX_DETECTIONS) {
            val offset = i * 6
            if (offset + 5 >= rawOutput.size) break
            val x1 = rawOutput[offset]
            val y1 = rawOutput[offset + 1]
            val x2 = rawOutput[offset + 2]
            val y2 = rawOutput[offset + 3]
            val confidence = rawOutput[offset + 4]
            val classId = rawOutput[offset + 5]
            if (confidence < CONF_THRESHOLD) continue
            if (classId != TEXT_CLASS_ID) continue
            val bx = ((x1 - padX) / scale).roundToInt().coerceIn(0, originalWidth)
            val by = ((y1 - padY) / scale).roundToInt().coerceIn(0, originalHeight)
            val bx2 = ((x2 - padX) / scale).roundToInt().coerceIn(0, originalWidth)
            val by2 = ((y2 - padY) / scale).roundToInt().coerceIn(0, originalHeight)
            val bw = bx2 - bx
            val bh = by2 - by
            if (bw < MIN_BOX_SIZE_PX || bh < MIN_BOX_SIZE_PX) continue
            boxes.add(DetectedBox(bx, by, bw, bh, confidence, isJapanese = true))
        }
        return boxes
    }

    /**
     * Non-maximum suppression to remove overlapping detections.
     * Keeps the highest-confidence box when IoU > threshold.
     */
    fun nonMaxSuppression(
        boxes: List<DetectedBox>,
        iouThreshold: Float = 0.5f,
    ): List<DetectedBox> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedByDescending { it.confidence }
        val kept = mutableListOf<DetectedBox>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (iou(sorted[i], sorted[j]) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    private fun iou(a: DetectedBox, b: DetectedBox): Float {
        val ix1 = maxOf(a.x, b.x)
        val iy1 = maxOf(a.y, b.y)
        val ix2 = minOf(a.x + a.width, b.x + b.width)
        val iy2 = minOf(a.y + a.height, b.y + b.height)
        val intersection = maxOf(0, ix2 - ix1) * maxOf(0, iy2 - iy1)
        if (intersection == 0) return 0f
        val union = a.width * a.height + b.width * b.height - intersection
        return intersection.toFloat() / union.toFloat()
    }

    fun addPadding(box: DetectedBox, imageWidth: Int, imageHeight: Int): DetectedBox {
        val pad = (maxOf(box.width, box.height) * PADDING_RATIO)
            .toInt().coerceIn(MIN_PADDING_PX, MAX_PADDING_PX)
        val x = (box.x - pad).coerceAtLeast(0)
        val y = (box.y - pad).coerceAtLeast(0)
        val w = (box.width + pad * 2).coerceAtMost(imageWidth - x)
        val h = (box.height + pad * 2).coerceAtMost(imageHeight - y)
        return box.copy(x = x, y = y, width = w, height = h)
    }

    fun computeTransform(imageWidth: Int, imageHeight: Int, inputSize: Int): YoloTransform {
        val scale = inputSize.toFloat() / maxOf(imageWidth, imageHeight)
        val scaledW = (imageWidth * scale).toInt().coerceAtLeast(1)
        val scaledH = (imageHeight * scale).toInt().coerceAtLeast(1)
        val padX = (inputSize - scaledW) / 2
        val padY = (inputSize - scaledH) / 2
        return YoloTransform(scale, padX, padY)
    }
}

data class YoloTransform(val scale: Float, val padX: Int, val padY: Int)
