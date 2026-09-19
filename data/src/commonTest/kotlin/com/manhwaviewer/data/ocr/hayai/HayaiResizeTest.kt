package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The crop resize, which has to be bit-identical to the reference, not merely close.
 *
 * The reference resizes a uint8 RGB image with PIL BILINEAR before patching. PIL does that in
 * fixed point, so resampling in floats and rounding afterwards lands 1 LSB off on ~15-20% of
 * pixels — small, but it feeds every `pixel_values` the encoder sees. Expected values come from
 * PIL for the same input.
 */
class HayaiResizeTest {

    private fun synthetic(count: Int) = IntArray(count) {
        ((it * 1103515245 + 12345) ushr 16) and 0xFF
    }

    private data class Case(
        val srcRows: Int,
        val srcCols: Int,
        val dstRows: Int,
        val dstCols: Int,
        val expected: List<Pair<Int, Int>>,
    )

    private val cases = listOf(
        Case(200, 120, 336, 192, listOf(0 to 0, 21503 to 94, 43007 to 108, 64511 to 107,
            86015 to 151, 107519 to 165, 129023 to 163, 150527 to 166, 172031 to 173, 193535 to 227)),
        Case(97, 241, 64, 160, listOf(0 to 37, 3413 to 145, 6826 to 114, 10239 to 162,
            13652 to 97, 17066 to 170, 20479 to 114, 23892 to 71, 27305 to 134, 30719 to 83)),
        Case(500, 500, 256, 256, listOf(0 to 111, 21845 to 118, 43690 to 128, 65535 to 129,
            87380 to 97, 109226 to 140, 131071 to 99, 152916 to 147, 174761 to 127, 196607 to 172)),
        Case(50, 400, 64, 512, listOf(0 to 0, 10922 to 118, 21845 to 90, 32767 to 154,
            43690 to 145, 54612 to 74, 65535 to 154, 76457 to 98, 87380 to 130, 98303 to 70)),
        Case(64, 64, 64, 64, listOf(0 to 0, 1365 to 96, 2730 to 192, 4095 to 32,
            5460 to 128, 6826 to 167, 8191 to 7, 9556 to 103, 10921 to 199, 12287 to 238)),
    )

    @Test
    fun resizeRgbMatchesPilExactly() {
        for (c in cases) {
            val src = synthetic(c.srcRows * c.srcCols * 3)
            val out = HayaiHost.resizeRgb(src, c.srcRows, c.srcCols, c.dstRows, c.dstCols)
            assertEquals(c.dstRows * c.dstCols * 3, out.size, "${c.srcRows}x${c.srcCols} size")
            for ((index, want) in c.expected) {
                assertEquals(
                    want, out[index],
                    "${c.srcRows}x${c.srcCols} -> ${c.dstRows}x${c.dstCols} at $index",
                )
            }
        }
    }

    @Test
    fun resizeRgbStaysInByteRange() {
        val c = cases[2]
        val out = HayaiHost.resizeRgb(synthetic(c.srcRows * c.srcCols * 3),
            c.srcRows, c.srcCols, c.dstRows, c.dstCols)
        assertTrue(out.all { it in 0..255 }, "resize produced a value outside 0..255")
    }

    @Test
    fun resizeRgbRefusesAMismatchedBuffer() {
        val threw = try {
            HayaiHost.resizeRgb(IntArray(10), 8, 8, 4, 4); false
        } catch (e: IllegalArgumentException) {
            true
        }
        assertTrue(threw, "a buffer that is not srcRows*srcCols*3 must be refused")
    }
}
