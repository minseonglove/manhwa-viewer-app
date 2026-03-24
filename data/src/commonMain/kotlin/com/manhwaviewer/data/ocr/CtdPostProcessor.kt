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

    fun extractFromDetMap(
        detChannel0: FloatArray,
        width: Int,
        height: Int,
        transform: CtdTransform,
        originalWidth: Int,
        originalHeight: Int,
        existingBoxes: List<DetectedBox>,
        threshold: Float = 0.3f,
        minArea: Int = 100,
    ): List<DetectedBox> {
        // 1. Binarize
        val mask = BooleanArray(width * height) { detChannel0[it] > threshold }

        // 2. Connected component labeling (union-find)
        val labels = IntArray(width * height) { -1 }
        val parent = IntArray(width * height) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }
            return r
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        var labelCount = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!mask[idx]) continue
                labels[idx] = idx
                if (x > 0 && mask[idx - 1]) union(idx, idx - 1)
                if (y > 0 && mask[idx - width]) union(idx, idx - width)
            }
        }

        // 3. Extract bounding boxes per component
        val componentBounds = mutableMapOf<Int, IntArray>() // root -> [minX, minY, maxX, maxY]
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (labels[idx] < 0) continue
                val root = find(idx)
                val bounds = componentBounds.getOrPut(root) { intArrayOf(x, y, x, y) }
                if (x < bounds[0]) bounds[0] = x
                if (y < bounds[1]) bounds[1] = y
                if (x > bounds[2]) bounds[2] = x
                if (y > bounds[3]) bounds[3] = y
            }
        }

        // 4. Map to original coordinates, filter small and overlapping
        return componentBounds.values.mapNotNull { bounds ->
            val bw = bounds[2] - bounds[0]
            val bh = bounds[3] - bounds[1]
            if (bw * bh < minArea) return@mapNotNull null

            val x1 = ((bounds[0] - transform.padX) / transform.scale).toInt().coerceIn(0, originalWidth)
            val y1 = ((bounds[1] - transform.padY) / transform.scale).toInt().coerceIn(0, originalHeight)
            val x2 = ((bounds[2] - transform.padX) / transform.scale).toInt().coerceIn(0, originalWidth)
            val y2 = ((bounds[3] - transform.padY) / transform.scale).toInt().coerceIn(0, originalHeight)
            val w = x2 - x1
            val h = y2 - y1
            if (w <= 0 || h <= 0) return@mapNotNull null

            // Only keep wide (horizontal) regions — titles, etc.
            if (w.toFloat() / h < 3.0f) return@mapNotNull null

            val candidate = DetectedBox(x1, y1, w, h, 0.5f, false)

            // Skip if contained within any existing blk box
            val contained = existingBoxes.any { blk ->
                x1 >= blk.x && y1 >= blk.y &&
                    x1 + w <= blk.x + blk.width && y1 + h <= blk.y + blk.height
            }
            if (contained) return@mapNotNull null

            // Skip if overlaps significantly
            val overlapping = existingBoxes.any { iou(it, candidate) > 0.3f }
            if (overlapping) return@mapNotNull null

            candidate
        }
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
