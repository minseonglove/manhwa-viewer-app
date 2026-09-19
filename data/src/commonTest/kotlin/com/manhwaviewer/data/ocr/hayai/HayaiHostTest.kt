package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The expected values here were produced by the reference implementation
 * (`hayai_ocr.litert`, the code the 499-item evaluation actually ran), not derived by hand.
 * The requirement for this port is to match that implementation, so the reference is the oracle.
 */
class HayaiHostTest {

    private fun assertClose(expected: Float, actual: Float, name: String, tol: Float = 1e-6f) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tol,
            "$name: expected $expected, got $actual (tol $tol)",
        )
    }

    // --- scaledSize: binary search for the largest scale fitting MAX_PATCHES -------------

    @Test
    fun scaledSize_matchesReferenceForRepresentativeCrops() {
        val cases = listOf(
            Triple(100 to 100, 256, 256),
            Triple(200 to 120, 320, 192),   // the 20x12 crop the parity harness uses
            Triple(64 to 64, 256, 256),
            Triple(1000 to 50, 1024, 64),   // extreme vertical
            Triple(50 to 1000, 64, 1024),   // extreme horizontal
            Triple(37 to 91, 160, 400),
            Triple(640 to 480, 288, 224),
            Triple(1 to 1, 112, 112),       // tiny: scale is capped, not unbounded
            Triple(2048 to 2048, 256, 256),
        )
        for ((size, th, tw) in cases) {
            val (h, w) = size
            val got = HayaiHost.scaledSize(h, w)
            assertEquals(th, got.first, "height for ${h}x$w")
            assertEquals(tw, got.second, "width for ${h}x$w")
        }
    }

    @Test
    fun scaledSize_neverExceedsThePatchBudget() {
        for (h in intArrayOf(1, 7, 37, 64, 100, 333, 1000, 2048)) {
            for (w in intArrayOf(1, 7, 37, 64, 100, 333, 1000, 2048)) {
                val (th, tw) = HayaiHost.scaledSize(h, w)
                assertEquals(0, th % HayaiHost.PATCH_SIZE, "height not a patch multiple for ${h}x$w")
                assertEquals(0, tw % HayaiHost.PATCH_SIZE, "width not a patch multiple for ${h}x$w")
                val patches = (th / HayaiHost.PATCH_SIZE) * (tw / HayaiHost.PATCH_SIZE)
                assertTrue(patches <= HayaiHost.MAX_PATCHES, "${h}x$w yields $patches patches")
            }
        }
    }

    // --- prefillMask: block-causal over [vision | text] ---------------------------------

    @Test
    fun prefillMask_hasTheReferenceBlockStructure() {
        val m = HayaiHost.prefillMask()
        assertEquals(HayaiHost.TOTAL * HayaiHost.TOTAL, m.size)
        fun at(r: Int, c: Int) = m[r * HayaiHost.TOTAL + c]

        assertEquals(0f, at(0, 0), "vision attends vision")
        assertEquals(0f, at(0, 255), "vision attends vision")
        assertEquals(HayaiHost.NEG_INF, at(0, 256), "vision must not attend text")
        assertEquals(0f, at(255, 255))
        assertEquals(0f, at(256, 0), "first text token attends vision")
        assertEquals(0f, at(256, 256), "first text token attends itself")
        assertEquals(HayaiHost.NEG_INF, at(256, 257), "first text token must not attend the future")
        assertEquals(0f, at(257, 0), "later text attends vision")
        assertEquals(HayaiHost.NEG_INF, at(257, 256), "later text rows are masked off the text block")
        assertEquals(0f, at(319, 255))
        assertEquals(HayaiHost.NEG_INF, at(319, 319))
    }

    @Test
    fun prefillMask_countsMatchTheReference() {
        val m = HayaiHost.prefillMask()
        assertEquals(81921, m.count { it == 0f }, "zero entries")
        assertEquals(20479, m.count { it == HayaiHost.NEG_INF }, "masked entries")
    }

    // --- decodeMask: everything up to and including pos is visible ----------------------

    @Test
    fun decodeMask_opensExactlyThePrefixThroughPos() {
        for (pos in intArrayOf(256, 257, 300, 319)) {
            val d = HayaiHost.decodeMask(pos)
            assertEquals(HayaiHost.TOTAL, d.size)
            assertEquals(pos + 1, d.count { it == 0f }, "visible count at pos=$pos")
            assertEquals(0f, d[pos], "pos itself must be visible at pos=$pos")
            if (pos + 1 < HayaiHost.TOTAL) {
                assertEquals(HayaiHost.NEG_INF, d[pos + 1], "the slot after pos must be masked")
            }
        }
    }

    // --- writeMask: the one-hot the CoreML decode graph takes instead of a `pos` scalar --

    @Test
    fun writeMask_selectsExactlySlotPos() {
        val w = HayaiHost.writeMask(300)
        assertEquals(HayaiHost.TOTAL, w.size)
        assertEquals(1, w.count { it == 1f })
        assertEquals(1f, w[300])
        assertEquals(0f, w[299])
        assertEquals(0f, w[301])
    }

    @Test
    fun writeMask_refusesASlotThatDoesNotExist() {
        for (pos in intArrayOf(-1, HayaiHost.TOTAL)) {
            val threw = try {
                HayaiHost.writeMask(pos); false
            } catch (e: IllegalArgumentException) {
                true
            }
            assertTrue(threw, "pos=$pos should be refused")
        }
    }

    // --- rope: 2D mRoPE over vision, 1D over text ---------------------------------------

    @Test
    fun rope_matchesReferenceSpotValuesFor16x16() {
        val (cos, sin) = HayaiHost.rope(16, 16)
        val d = HayaiHost.D_AXIS
        assertEquals(HayaiHost.TOTAL * d, cos.size)
        assertClose(1.000000000f, cos[0 * d + 0], "cos[0][0]")
        assertClose(0.000000000f, sin[0 * d + 0], "sin[0][0]")
        assertClose(1.000000000f, cos[1 * d + 0], "cos[1][0]")
        assertClose(0.540302277f, cos[1 * d + 16], "cos[1][16]")
        assertClose(0.841471016f, sin[1 * d + 16], "sin[1][16]")
        assertClose(-0.759687901f, cos[255 * d + 0], "cos[255][0]")
        assertClose(0.650287867f, sin[255 * d + 0], "sin[255][0]")
        assertClose(0.999996424f, cos[255 * d + 31], "cos[255][31]")
        assertClose(0.002667416f, sin[255 * d + 31], "sin[255][31]")
    }

    @Test
    fun rope_textPositionsAreIndependentOfTheVisionGrid() {
        // Text rows start at MAX_PATCHES and use 1D RoPE, so they must not move with ph/pw.
        for ((ph, pw) in listOf(16 to 16, 20 to 12, 4 to 4)) {
            val (cos, sin) = HayaiHost.rope(ph, pw)
            val d = HayaiHost.D_AXIS
            assertClose(1.000000000f, cos[256 * d + 0], "cos[256][0] at ${ph}x$pw")
            assertClose(1.000000000f, cos[256 * d + 1], "cos[256][1] at ${ph}x$pw")
            assertClose(0.540302277f, cos[257 * d + 0], "cos[257][0] at ${ph}x$pw")
            assertClose(0.841471016f, sin[257 * d + 0], "sin[257][0] at ${ph}x$pw")
            assertClose(0.985896587f, cos[319 * d + 0], "cos[319][0] at ${ph}x$pw")
            assertClose(0.167355701f, sin[319 * d + 0], "sin[319][0] at ${ph}x$pw")
        }
    }

    @Test
    fun rope_matchesReferenceForANonSquareGrid() {
        val (cos, sin) = HayaiHost.rope(20, 12)
        val d = HayaiHost.D_AXIS
        assertClose(0.988704622f, cos[239 * d + 0], "cos[239][0]")
        assertClose(0.149877205f, sin[239 * d + 0], "sin[239][0]")
        assertClose(0.999998093f, cos[239 * d + 31], "cos[239][31]")
        assertClose(0.001956106f, sin[239 * d + 31], "sin[239][31]")
    }

    // --- encoderInput: normalise, patchify, pad ----------------------------------------

    /** Source pixel (y, x, c) carries its own flat index, so a transpose error is visible. */
    private fun rampRgb(patchRows: Int, patchCols: Int): FloatArray {
        val h = patchRows * HayaiHost.PATCH_SIZE
        val w = patchCols * HayaiHost.PATCH_SIZE
        return FloatArray(h * w * 3) { it.toFloat() }
    }

    @Test
    fun encoderInput_laysPatchesOutInTheReferenceOrder() {
        // The reference reshapes (ph,16,pw,16,3) then transposes to (ph,pw,16,16,3): each patch
        // holds its own 16x16 window, rows contiguous. Values are the raw ramp, un-normalised,
        // so the assertion is about placement only.
        val raw = HayaiHost.patchify(rampRgb(2, 3), 2, 3)
        val stride = 768
        val expected = mapOf(
            0 to listOf(0f, 1f, 2f, 3f),
            1 to listOf(48f, 49f, 50f, 51f),
            2 to listOf(96f, 97f, 98f, 99f),
            3 to listOf(2304f, 2305f, 2306f, 2307f),
            4 to listOf(2352f, 2353f, 2354f, 2355f),
            5 to listOf(2400f, 2401f, 2402f, 2403f),
        )
        for ((patch, head) in expected) {
            for ((i, v) in head.withIndex()) {
                assertEquals(v, raw[patch * stride + i], "patch[$patch][$i]")
            }
        }
        assertEquals(47f, raw[0 * stride + 47], "patch[0][47] ends the first source row")
        assertEquals(2207f, raw[0 * stride + 767], "patch[0][767] is the patch's last pixel")
        assertEquals(4607f, raw[5 * stride + 767], "patch[5][767]")
    }

    @Test
    fun encoderInput_normalisesToMinusOneOne() {
        val pixels = FloatArray(HayaiHost.PATCH_SIZE * HayaiHost.PATCH_SIZE * 3) { 0f }
        pixels[0] = 0f; pixels[1] = 127f; pixels[2] = 128f; pixels[3] = 255f
        val out = HayaiHost.encoderInput(pixels, 1, 1)
        assertClose(-1.000000000f, out[0], "0 -> -1")
        assertClose(-0.003921569f, out[1], "127")
        assertClose(0.003921569f, out[2], "128")
        assertClose(1.000000000f, out[3], "255 -> +1")
    }

    @Test
    fun encoderInput_padsUnusedPatchesWithZero() {
        val out = HayaiHost.encoderInput(rampRgb(2, 3), 2, 3)
        assertEquals(HayaiHost.MAX_PATCHES * 768, out.size)
        for (slot in 6 until HayaiHost.MAX_PATCHES) {
            assertEquals(0f, out[slot * 768], "slot $slot must be zero padding")
        }
    }

    @Test
    fun encoderInput_refusesPixelsThatDoNotMatchTheGrid() {
        val threw = try {
            HayaiHost.encoderInput(FloatArray(10), 2, 3); false
        } catch (e: IllegalArgumentException) {
            true
        }
        assertTrue(threw, "a pixel buffer that is not ph*16 x pw*16 x 3 must be refused")
    }

    @Test
    fun attentionMask_marksExactlyTheRealPatches() {
        val mask = HayaiHost.attentionMask(2, 3)
        assertEquals(HayaiHost.MAX_PATCHES, mask.size)
        assertEquals(6, mask.count { it == 1 })
        for (i in 0 until 6) assertEquals(1, mask[i], "patch $i is real")
        for (i in 6 until HayaiHost.MAX_PATCHES) assertEquals(0, mask[i], "slot $i is padding")
    }

    // --- resample: PIL's filter, not plain bilinear -------------------------------------

    @Test
    fun resample_matchesPilForDownscalingAxes() {
        // Plain bilinear differs from the reference by up to ~0.9 on this input once an axis
        // shrinks, because PIL widens the triangle filter by the reduction factor. These
        // expectations come from PIL, so a plain-bilinear implementation cannot pass.
        val src = FloatArray(16 * 16) { it.toFloat() }
        data class Case(val rows: Int, val cols: Int, val at: List<Triple<Int, Int, Float>>)
        val cases = listOf(
            Case(20, 12, listOf(
                Triple(0, 0, 0.300000f), Triple(0, 11, 14.700000f),
                Triple(19, 0, 240.300003f), Triple(19, 11, 254.699997f),
                Triple(10, 6, 134.581818f))),
            Case(8, 8, listOf(
                Triple(0, 0, 12.142857f), Triple(0, 7, 25.714287f),
                Triple(7, 0, 229.285706f), Triple(7, 7, 242.857147f),
                Triple(4, 4, 144.500000f))),
            Case(4, 64, listOf(
                Triple(0, 0, 30.285715f), Triple(0, 63, 45.285713f),
                Triple(3, 0, 209.714279f), Triple(3, 63, 224.714279f),
                Triple(2, 32, 159.625000f))),
        )
        for (c in cases) {
            val out = HayaiHost.resample(src, 16, 16, c.rows, c.cols)
            assertEquals(c.rows * c.cols, out.size)
            for ((y, x, want) in c.at) {
                assertClose(want, out[y * c.cols + x], "${c.rows}x${c.cols} out[$y][$x]", 1e-3f)
            }
        }
    }

    @Test
    fun resample_isIdentityAtTheSameSize() {
        val src = FloatArray(16 * 16) { it.toFloat() }
        val out = HayaiHost.resample(src, 16, 16, 16, 16)
        for (i in src.indices) assertClose(src[i], out[i], "identity at $i")
    }

    @Test
    fun positionEmbeddings_padsWithTheFirstPatchNotZero() {
        // The reference fills unused slots with out[0:1], not zeros. Padding with zeros would
        // be a silent change to what the encoder sees in every crop smaller than 16x16.
        val base = FloatArray(16 * 16 * 768) { it.toFloat() }
        val pos = HayaiHost.positionEmbeddings(base, 2, 3)
        assertEquals(HayaiHost.MAX_PATCHES * 768, pos.size)
        val n = 6
        for (slot in n until HayaiHost.MAX_PATCHES) {
            for (c in intArrayOf(0, 1, 767)) {
                assertEquals(
                    pos[c], pos[slot * 768 + c],
                    "slot $slot channel $c must repeat the first patch",
                )
            }
        }
        assertTrue(pos[0] != 0f, "the first patch embedding is not zero, so the test is meaningful")
    }

    @Test
    fun positionEmbeddings_isTheBaseGridUnchangedAt16x16() {
        val base = FloatArray(16 * 16 * 768) { it.toFloat() }
        val pos = HayaiHost.positionEmbeddings(base, 16, 16)
        for (i in intArrayOf(0, 1, 767, 768, 100_000, 16 * 16 * 768 - 1)) {
            assertClose(base[i], pos[i], "index $i")
        }
    }

    @Test
    fun rope_padsUnusedVisionSlotsAsIdentity() {
        // 20x12 = 240 patches; slots 240..255 are padding and must not rotate anything.
        val (cos, sin) = HayaiHost.rope(20, 12)
        val d = HayaiHost.D_AXIS
        for (slot in 240 until HayaiHost.MAX_PATCHES) {
            assertClose(1f, cos[slot * d + 0], "cos[$slot][0] padding")
            assertClose(0f, sin[slot * d + 0], "sin[$slot][0] padding")
        }
    }
}
