package com.manhwaviewer.data.ocr

object YoloPostProcessor {

    private const val CONF_THRESHOLD = 0.3f
    private const val TEXT_CLASS_ID = 1f
    private const val PADDING_RATIO = 0.08f
    private const val MIN_PADDING_PX = 4
    private const val MAX_PADDING_PX = 20

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
            val bx = ((x1 - padX) / scale).toInt().coerceIn(0, originalWidth)
            val by = ((y1 - padY) / scale).toInt().coerceIn(0, originalHeight)
            val bx2 = ((x2 - padX) / scale).toInt().coerceIn(0, originalWidth)
            val by2 = ((y2 - padY) / scale).toInt().coerceIn(0, originalHeight)
            val bw = bx2 - bx
            val bh = by2 - by
            if (bw <= 0 || bh <= 0) continue
            boxes.add(DetectedBox(bx, by, bw, bh, confidence, isJapanese = true))
        }
        return boxes
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
