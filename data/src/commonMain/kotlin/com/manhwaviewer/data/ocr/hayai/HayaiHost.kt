package com.manhwaviewer.data.ocr.hayai

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Host-side tensors the Hayai OCR graphs take, shared by both platforms.
 *
 * The graphs differ only in container format (LiteRT on Android, CoreML on iOS); the model,
 * its weights and this host arithmetic are the same, so it lives in commonMain and only graph
 * execution is expect/actual. This mirrors the reference implementation, where the CoreML path
 * inherits the LiteRT engine's generate loop verbatim and swaps nothing but the tensor calls.
 *
 * Everything is returned flat, in the row-major order the graph inputs declare, so a runner can
 * hand it to a tensor without reshaping.
 */
object HayaiHost {

    const val PATCH_SIZE = 16
    const val MAX_PATCHES = 256
    const val MAX_TEXT = 64
    const val TOTAL = MAX_PATCHES + MAX_TEXT
    const val D_AXIS = 32

    /** Values per patch: 16 x 16 pixels x 3 channels. */
    const val PATCH_VALUES = PATCH_SIZE * PATCH_SIZE * 3

    /** The mask's "cannot attend" value. Not -Inf: the graphs were exported with this constant. */
    const val NEG_INF = -1e9f

    private const val ROPE_THETA = 10000.0

    /**
     * The (height, width) a crop is resized to before patching, both multiples of [PATCH_SIZE].
     *
     * NaFlex keeps the aspect ratio and picks the largest scale whose patch count still fits
     * [MAX_PATCHES], found by bisection because the patch count is a step function of the scale.
     */
    fun scaledSize(height: Int, width: Int): Pair<Int, Int> {
        val eps = 1e-5
        var lo = eps / 10
        var hi = 100.0
        while (hi - lo >= eps) {
            val mid = (lo + hi) / 2
            val th = scaleTo(mid, height)
            val tw = scaleTo(mid, width)
            if ((th / PATCH_SIZE) * (tw / PATCH_SIZE) <= MAX_PATCHES) lo = mid else hi = mid
        }
        return scaleTo(lo, height) to scaleTo(lo, width)
    }

    private fun scaleTo(scale: Double, size: Int): Int =
        maxOf(ceil((size * scale) / PATCH_SIZE).toInt() * PATCH_SIZE, PATCH_SIZE)

    /**
     * Resize a uint8 RGB crop, matching the reference's PIL `BILINEAR` bit for bit.
     *
     * Not the same code path as [resample]. PIL resizes 8-bit images in fixed point, and
     * resampling in floats and rounding afterwards lands 1 LSB off on roughly a fifth of the
     * pixels — harmless-looking, but it feeds every `pixel_values` the encoder sees, and the
     * point of this port is to reproduce the reference rather than approximate it.
     *
     * [rgb] and the result are HWC, values 0..255.
     */
    fun resizeRgb(rgb: IntArray, srcRows: Int, srcCols: Int, dstRows: Int, dstCols: Int): IntArray {
        require(rgb.size == srcRows * srcCols * 3) {
            "expected ${srcRows * srcCols * 3} values for ${srcRows}x$srcCols RGB, got ${rgb.size}"
        }
        val horizontal = resizePass8(rgb, srcRows, srcCols, dstCols)
        val transposed = IntArray(dstCols * srcRows * 3)
        for (r in 0 until srcRows) {
            for (c in 0 until dstCols) {
                val from = (r * dstCols + c) * 3
                val to = (c * srcRows + r) * 3
                transposed[to] = horizontal[from]
                transposed[to + 1] = horizontal[from + 1]
                transposed[to + 2] = horizontal[from + 2]
            }
        }
        val vertical = resizePass8(transposed, dstCols, srcRows, dstRows)
        val out = IntArray(dstRows * dstCols * 3)
        for (c in 0 until dstCols) {
            for (r in 0 until dstRows) {
                val from = (c * dstRows + r) * 3
                val to = (r * dstCols + c) * 3
                out[to] = vertical[from]
                out[to + 1] = vertical[from + 1]
                out[to + 2] = vertical[from + 2]
            }
        }
        return out
    }

    /**
     * One horizontal 8-bit pass, in PIL's fixed point.
     *
     * Weights are normalised as floats, then converted with PIL's rounding and accumulated as
     * integers with a half added up front, so the shift at the end rounds rather than truncates.
     */
    private fun resizePass8(src: IntArray, rows: Int, inSize: Int, outSize: Int): IntArray {
        val scale = inSize.toDouble() / outSize
        val filterScale = maxOf(scale, 1.0)
        val support = filterScale
        val out = IntArray(rows * outSize * 3)
        val weights = DoubleArray(inSize)
        val fixed = IntArray(inSize)
        val half = 1 shl (PRECISION_BITS - 1)
        for (i in 0 until outSize) {
            val center = (i + 0.5) * scale
            val from = maxOf((center - support + 0.5).toInt(), 0)
            val to = minOf(ceil(center + support + 0.5).toInt(), inSize)
            var total = 0.0
            for (k in from until to) {
                val w = 1.0 - abs((k - center + 0.5) / filterScale)
                weights[k] = if (w > 0.0) w else 0.0
                total += weights[k]
            }
            for (k in from until to) {
                val w = if (total != 0.0) weights[k] / total else weights[k]
                val scaled = w * (1 shl PRECISION_BITS)
                fixed[k] = if (w < 0) (scaled - 0.5).toInt() else (scaled + 0.5).toInt()
            }
            for (r in 0 until rows) {
                val rowBase = r * inSize * 3
                val outBase = (r * outSize + i) * 3
                for (channel in 0 until 3) {
                    var acc = half
                    for (k in from until to) acc += src[rowBase + k * 3 + channel] * fixed[k]
                    out[outBase + channel] = clip8(acc)
                }
            }
        }
        return out
    }

    private fun clip8(value: Int): Int {
        val shifted = value shr PRECISION_BITS
        return if (shifted < 0) 0 else if (shifted > 255) 255 else shifted
    }

    /** PIL's `PRECISION_BITS` for 8-bit resampling: `32 - 8 - 2`. */
    private const val PRECISION_BITS = 22

    /**
     * Resample one 2-D plane, matching the reference's PIL `BILINEAR` exactly.
     *
     * Not plain bilinear. When an axis shrinks, PIL widens the triangle filter by the reduction
     * factor and renormalises, which is what makes the result antialiased; plain bilinear
     * differs by up to ~0.9 on unit-scale data for the grids this model uses. Since Android and
     * iOS must produce identical embeddings, the filter is implemented here rather than handed
     * to a platform image API with its own resampling semantics.
     */
    fun resample(src: FloatArray, srcRows: Int, srcCols: Int, dstRows: Int, dstCols: Int): FloatArray {
        require(src.size == srcRows * srcCols) { "expected ${srcRows * srcCols} values, got ${src.size}" }
        val horizontal = resamplePass(src, srcRows, srcCols, dstCols)
        val transposed = FloatArray(dstCols * srcRows)
        for (r in 0 until srcRows) {
            for (c in 0 until dstCols) transposed[c * srcRows + r] = horizontal[r * dstCols + c]
        }
        val vertical = resamplePass(transposed, dstCols, srcRows, dstRows)
        val out = FloatArray(dstRows * dstCols)
        for (c in 0 until dstCols) {
            for (r in 0 until dstRows) out[r * dstCols + c] = vertical[c * dstRows + r]
        }
        return out
    }

    /** One horizontal pass: every row of [src] resampled from [inSize] to [outSize] columns. */
    private fun resamplePass(src: FloatArray, rows: Int, inSize: Int, outSize: Int): FloatArray {
        val scale = inSize.toDouble() / outSize
        val filterScale = maxOf(scale, 1.0)
        val support = filterScale  // triangle filter support is 1.0 before scaling
        val out = FloatArray(rows * outSize)
        val weights = DoubleArray(inSize)
        for (i in 0 until outSize) {
            val center = (i + 0.5) * scale
            val from = maxOf((center - support + 0.5).toInt(), 0)
            val to = minOf(ceil(center + support + 0.5).toInt(), inSize)
            var total = 0.0
            for (k in from until to) {
                val w = 1.0 - abs((k - center + 0.5) / filterScale)
                weights[k] = if (w > 0.0) w else 0.0
                total += weights[k]
            }
            if (total != 0.0) for (k in from until to) weights[k] /= total
            for (r in 0 until rows) {
                var acc = 0.0
                val base = r * inSize
                for (k in from until to) acc += weights[k] * src[base + k]
                out[r * outSize + i] = acc.toFloat()
            }
        }
        return out
    }

    /**
     * The encoder's `pos_embeds`, flat `MAX_PATCHES * 768`.
     *
     * [base] is the checkpoint's 16x16 grid, flat `16 * 16 * 768`, resampled per channel to the
     * crop's patch grid. Unused slots repeat the first patch's embedding rather than sitting at
     * zero, which is what the reference does.
     */
    fun positionEmbeddings(base: FloatArray, patchRows: Int, patchCols: Int): FloatArray {
        val channels = 768
        val side = 16
        require(base.size == side * side * channels) { "expected ${side * side * channels} values, got ${base.size}" }
        val count = patchRows * patchCols
        require(count in 1..MAX_PATCHES) { "$patchRows x $patchCols is $count patches, not in 1..$MAX_PATCHES" }

        val out = FloatArray(MAX_PATCHES * channels)
        val plane = FloatArray(side * side)
        for (c in 0 until channels) {
            for (i in 0 until side * side) plane[i] = base[i * channels + c]
            val resized = if (patchRows == side && patchCols == side) plane
            else resample(plane, side, side, patchRows, patchCols)
            for (slot in 0 until count) out[slot * channels + c] = resized[slot]
        }
        for (slot in count until MAX_PATCHES) {
            out.copyInto(out, destinationOffset = slot * channels, startIndex = 0, endIndex = channels)
        }
        return out
    }

    /**
     * Pack a resized RGB buffer into patch-major order, flat `patchRows * patchCols * 768`.
     *
     * Each patch holds its own 16x16 window with rows contiguous, which is the reference's
     * `(ph,16,pw,16,3) -> transpose(0,2,1,3,4)`. Values pass through unchanged; [encoderInput]
     * is the normalising entry point.
     */
    fun patchify(rgb: FloatArray, patchRows: Int, patchCols: Int): FloatArray {
        val width = patchCols * PATCH_SIZE
        require(rgb.size == patchRows * PATCH_SIZE * width * 3) {
            "expected ${patchRows * PATCH_SIZE * width * 3} values for a " +
                "${patchRows}x$patchCols patch grid, got ${rgb.size}"
        }
        val out = FloatArray(patchRows * patchCols * PATCH_VALUES)
        for (patchRow in 0 until patchRows) {
            for (patchCol in 0 until patchCols) {
                val patchBase = (patchRow * patchCols + patchCol) * PATCH_VALUES
                for (y in 0 until PATCH_SIZE) {
                    val srcRow = (patchRow * PATCH_SIZE + y) * width + patchCol * PATCH_SIZE
                    rgb.copyInto(
                        destination = out,
                        destinationOffset = patchBase + y * PATCH_SIZE * 3,
                        startIndex = srcRow * 3,
                        endIndex = (srcRow + PATCH_SIZE) * 3,
                    )
                }
            }
        }
        return out
    }

    /**
     * The encoder's `pixel_values`, flat `MAX_PATCHES * 768`.
     *
     * [rgb] is the already-resized crop in HWC order with values in 0..255; resizing belongs to
     * the platform image layer. Values are scaled to [-1, 1] and unused patch slots stay zero.
     */
    fun encoderInput(rgb: FloatArray, patchRows: Int, patchCols: Int): FloatArray {
        val packed = patchify(rgb, patchRows, patchCols)
        val out = FloatArray(MAX_PATCHES * PATCH_VALUES)
        for (i in packed.indices) out[i] = (packed[i] / 255f - 0.5f) / 0.5f
        return out
    }

    /** The encoder's `attention_mask`, flat `MAX_PATCHES`: 1 for a real patch, 0 for padding. */
    fun attentionMask(patchRows: Int, patchCols: Int): IntArray {
        val count = patchRows * patchCols
        require(count in 1..MAX_PATCHES) { "$patchRows x $patchCols is $count patches, not in 1..$MAX_PATCHES" }
        return IntArray(MAX_PATCHES) { if (it < count) 1 else 0 }
    }

    /**
     * [attentionMask] as an additive mask: 0 where a patch is real, [NEG_INF] where it is padding.
     * The LiteRT encoder takes it in this form so no int64 comparison stays in the graph.
     */
    fun additiveMask(attentionMask: IntArray): FloatArray =
        FloatArray(attentionMask.size) { if (attentionMask[it] == 0) NEG_INF else 0f }

    /**
     * Block-causal attention mask for prefill, flat `TOTAL * TOTAL`.
     *
     * Vision tokens see each other and nothing else. The one text token being prefilled sees the
     * vision block and itself. The remaining text slots are placeholders: they see the vision
     * block so the rows stay well-formed, but nothing in the text block.
     */
    fun prefillMask(): FloatArray {
        val mask = FloatArray(TOTAL * TOTAL) { NEG_INF }
        for (row in 0 until TOTAL) {
            val visible = when {
                row < MAX_PATCHES -> MAX_PATCHES
                row == MAX_PATCHES -> MAX_PATCHES + 1
                else -> MAX_PATCHES
            }
            val base = row * TOTAL
            for (col in 0 until visible) mask[base + col] = 0f
        }
        return mask
    }

    /** Decode-step mask, flat `TOTAL`: every slot up to and including [pos] is visible. */
    fun decodeMask(pos: Int): FloatArray {
        require(pos in 0 until TOTAL) { "pos $pos is outside the cache [0, $TOTAL)" }
        val mask = FloatArray(TOTAL) { NEG_INF }
        for (i in 0..pos) mask[i] = 0f
        return mask
    }

    /**
     * The cache slot [pos] names, as a one-hot of length [TOTAL].
     *
     * The CoreML decode graph takes this where the LiteRT graph takes a `pos` scalar: deriving
     * the slot in-graph needs index arithmetic that does not convert, so the host computes the
     * same one-hot a step earlier. Kept here rather than in the iOS runner because it is
     * arithmetic, not platform API.
     */
    fun writeMask(pos: Int): FloatArray {
        require(pos in 0 until TOTAL) { "pos $pos is outside the cache [0, $TOTAL)" }
        return FloatArray(TOTAL).also { it[pos] = 1f }
    }

    /**
     * mRoPE tables, each flat `TOTAL * D_AXIS`.
     *
     * Vision slots carry a 2D rotation: the first half of the axis encodes the patch row, the
     * second half the patch column. Text slots carry the usual 1D rotation, duplicated across
     * both halves. Slots past the crop's patch count stay at cos 1 / sin 0 so padding does not
     * rotate anything.
     */
    fun rope(patchRows: Int, patchCols: Int): Pair<FloatArray, FloatArray> {
        val half = D_AXIS / 2
        val freqs = DoubleArray(half) { 1.0 / ROPE_THETA.pow((it * 2).toDouble() / D_AXIS) }

        val cosOut = FloatArray(TOTAL * D_AXIS) { 1f }
        val sinOut = FloatArray(TOTAL * D_AXIS)

        for (slot in 0 until patchRows * patchCols) {
            val row = slot / patchCols
            val col = slot % patchCols
            val base = slot * D_AXIS
            for (k in 0 until half) {
                val y = row * freqs[k]
                val x = col * freqs[k]
                cosOut[base + k] = cos(y).toFloat()
                sinOut[base + k] = sin(y).toFloat()
                cosOut[base + half + k] = cos(x).toFloat()
                sinOut[base + half + k] = sin(x).toFloat()
            }
        }
        for (t in 0 until MAX_TEXT) {
            val base = (MAX_PATCHES + t) * D_AXIS
            for (k in 0 until half) {
                val angle = t * freqs[k]
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                cosOut[base + k] = c
                sinOut[base + k] = s
                cosOut[base + half + k] = c
                sinOut[base + half + k] = s
            }
        }
        return cosOut to sinOut
    }
}
