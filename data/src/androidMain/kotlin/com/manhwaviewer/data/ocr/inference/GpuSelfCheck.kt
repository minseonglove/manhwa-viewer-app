package com.manhwaviewer.data.ocr.inference

import com.manhwaviewer.data.ocr.DetectedBox
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Comparisons a GPU model's check output must pass before the app keeps it. */
object GpuSelfCheck {

    /**
     * Mean |got − ref| over mean |ref|. Infinite if [got] holds a NaN or infinity: fp16 overflow
     * turned every value of one broken encoder into NaN, and a NaN would otherwise just vanish
     * from the mean.
     */
    fun relativeError(got: FloatArray, ref: FloatArray): Float {
        require(got.size == ref.size) { "got ${got.size} values, reference has ${ref.size}" }
        var diff = 0.0
        var scale = 0.0
        for (i in got.indices) {
            if (!got[i].isFinite()) return Float.POSITIVE_INFINITY
            diff += abs(got[i] - ref[i])
            scale += abs(ref[i])
        }
        if (scale == 0.0) return if (diff == 0.0) 0f else Float.POSITIVE_INFINITY
        return (diff / scale).toFloat()
    }

    /** Same number of boxes, and each box of [ref] overlaps its own box of [got] by at least [minIou]. */
    fun boxesAgree(got: List<DetectedBox>, ref: List<DetectedBox>, minIou: Float = 0.7f): Boolean {
        if (got.size != ref.size) return false
        val used = BooleanArray(got.size)
        for (r in ref) {
            val match = got.indices.filter { !used[it] }.maxByOrNull { iou(got[it], r) } ?: return false
            if (iou(got[match], r) < minIou) return false
            used[match] = true
        }
        return true
    }

    private fun iou(a: DetectedBox, b: DetectedBox): Float {
        val w = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
        val h = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
        if (w <= 0 || h <= 0) return 0f
        val inter = w.toFloat() * h
        return inter / (a.width.toFloat() * a.height + b.width.toFloat() * b.height - inter)
    }
}
