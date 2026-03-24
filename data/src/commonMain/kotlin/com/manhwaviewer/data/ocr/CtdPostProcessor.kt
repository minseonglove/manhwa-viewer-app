package com.manhwaviewer.data.ocr

data class CtdTransform(
    val scale: Float,
    val padX: Int,
    val padY: Int,
)

data class DetectedBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val confidence: Float,
    val isJapanese: Boolean,
)

object CtdPostProcessor {

    private const val CONF_THRESHOLD = 0.3f
    private const val NMS_IOU_THRESHOLD = 0.45f

    fun processBlkOutput(
        blk: FloatArray,
        numAnchors: Int,
        transform: CtdTransform,
        originalWidth: Int,
        originalHeight: Int,
    ): List<DetectedBox> {
        val candidates = mutableListOf<DetectedBox>()

        for (i in 0 until numAnchors) {
            val offset = i * 7
            val cx = blk[offset]
            val cy = blk[offset + 1]
            val w = blk[offset + 2]
            val h = blk[offset + 3]
            val objConf = blk[offset + 4]
            val clsEng = blk[offset + 5]
            val clsJpn = blk[offset + 6]

            val isJapanese = clsJpn > clsEng
            val clsConf = if (isJapanese) clsJpn else clsEng
            val confidence = objConf * clsConf
            if (confidence < CONF_THRESHOLD) continue

            val x1 = ((cx - w / 2 - transform.padX) / transform.scale).toInt()
            val y1 = ((cy - h / 2 - transform.padY) / transform.scale).toInt()
            val x2 = ((cx + w / 2 - transform.padX) / transform.scale).toInt()
            val y2 = ((cy + h / 2 - transform.padY) / transform.scale).toInt()

            val bx = x1.coerceIn(0, originalWidth)
            val by = y1.coerceIn(0, originalHeight)
            val bw = (x2 - x1).coerceIn(0, originalWidth - bx)
            val bh = (y2 - y1).coerceIn(0, originalHeight - by)
            if (bw <= 0 || bh <= 0) continue

            candidates.add(DetectedBox(bx, by, bw, bh, confidence, isJapanese))
        }

        return nms(candidates)
    }

    private fun nms(boxes: List<DetectedBox>): List<DetectedBox> {
        val sorted = boxes.sortedByDescending { it.confidence }
        val kept = mutableListOf<DetectedBox>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (iou(sorted[i], sorted[j]) > NMS_IOU_THRESHOLD) {
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
}
