package com.manhwaviewer.data.ocr

import kotlin.math.roundToInt

data class DbTransform(
    val scaleW: Float,
    val scaleH: Float,
    val padW: Int,
    val padH: Int,
)

object DbPostProcessor {

    fun process(
        probMap: FloatArray,
        mapH: Int,
        mapW: Int,
        originalH: Int,
        originalW: Int,
        thresh: Float = PaddleOcrConfig.DB_THRESH,
        boxThresh: Float = PaddleOcrConfig.DB_BOX_THRESH,
        unclipRatio: Float = PaddleOcrConfig.DB_UNCLIP_RATIO,
        minSize: Int = PaddleOcrConfig.DB_MIN_SIZE,
    ): List<DetectedBox> {
        // 1. 이진화
        val mask = BooleanArray(mapH * mapW) { probMap[it] > thresh }

        // 2. 연결 컴포넌트 라벨링 (two-pass + union-find)
        val labels = IntArray(mapH * mapW)
        val parent = IntArray(mapH * mapW + 1)
        var nextLabel = 1

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (c != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        // Pass 1: 라벨 할당
        for (y in 0 until mapH) {
            for (x in 0 until mapW) {
                val idx = y * mapW + x
                if (!mask[idx]) continue

                val top = if (y > 0 && mask[(y - 1) * mapW + x]) labels[(y - 1) * mapW + x] else 0
                val left = if (x > 0 && mask[y * mapW + x - 1]) labels[y * mapW + x - 1] else 0

                when {
                    top == 0 && left == 0 -> {
                        labels[idx] = nextLabel
                        parent[nextLabel] = nextLabel
                        nextLabel++
                    }
                    top != 0 && left == 0 -> labels[idx] = top
                    top == 0 && left != 0 -> labels[idx] = left
                    else -> {
                        labels[idx] = top
                        if (top != left) union(top, left)
                    }
                }
            }
        }

        // Pass 2: 라벨 정규화 + 바운딩 박스 수집
        data class BBox(var minX: Int, var minY: Int, var maxX: Int, var maxY: Int, var scoreSum: Float, var count: Int)

        val boxes = mutableMapOf<Int, BBox>()
        for (y in 0 until mapH) {
            for (x in 0 until mapW) {
                val idx = y * mapW + x
                if (labels[idx] == 0) continue
                val root = find(labels[idx])
                labels[idx] = root
                val box = boxes.getOrPut(root) { BBox(x, y, x, y, 0f, 0) }
                if (x < box.minX) box.minX = x
                if (y < box.minY) box.minY = y
                if (x > box.maxX) box.maxX = x
                if (y > box.maxY) box.maxY = y
                box.scoreSum += probMap[idx]
                box.count++
            }
        }

        // 3. 필터링 + unclip + 좌표 변환
        val scaleX = originalW.toFloat() / mapW
        val scaleY = originalH.toFloat() / mapH
        val result = mutableListOf<DetectedBox>()

        for ((_, bbox) in boxes) {
            val avgScore = bbox.scoreSum / bbox.count
            if (avgScore < boxThresh) continue

            val bw = bbox.maxX - bbox.minX + 1
            val bh = bbox.maxY - bbox.minY + 1
            if (minOf(bw, bh) < minSize) continue

            // Unclip: 박스 확장
            val area = bw * bh
            val perimeter = 2f * (bw + bh)
            val offset = (area * unclipRatio / perimeter).roundToInt()

            val x1 = ((bbox.minX - offset) * scaleX).roundToInt().coerceIn(0, originalW)
            val y1 = ((bbox.minY - offset) * scaleY).roundToInt().coerceIn(0, originalH)
            val x2 = ((bbox.maxX + 1 + offset) * scaleX).roundToInt().coerceIn(0, originalW)
            val y2 = ((bbox.maxY + 1 + offset) * scaleY).roundToInt().coerceIn(0, originalH)

            val finalW = x2 - x1
            val finalH = y2 - y1
            if (finalW <= 0 || finalH <= 0) continue

            result.add(DetectedBox(x1, y1, finalW, finalH, avgScore, isJapanese = true))
        }

        return result
    }
}
