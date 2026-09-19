package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole host stage, on inputs the size and shape real crops produce.
 *
 * The earlier unit tests pin each function on hand-sized cases. This one runs the full chain a
 * crop takes — resize grid, patchify, normalise, position-embedding resample, mRoPE — at five
 * patch grids drawn from the 499-item evaluation set, and compares against the reference
 * implementation's output for the same input.
 *
 * Both sides feed the same graphs, so if the host tensors agree the recognised text agrees;
 * this is what stands in for an end-to-end comparison until the graphs run under Kotlin.
 *
 * The input is synthesised from a 32-bit LCG rather than taken from the evaluation pages: the
 * pages are copyrighted, and the host arithmetic does not care whether the pixels are real —
 * only the grid and the value range matter.
 */
class HayaiHostPipelineTest {

    /** Must produce the same bytes as the generator that made the expected values. */
    private fun synthetic(count: Int) = FloatArray(count) {
        (((it * 1103515245 + 12345) ushr 16) and 0xFF).toFloat()
    }

    /** The reference's `position_base`, flattened 16x16x768. Values are irrelevant to shape. */
    private fun positionBase() = FloatArray(16 * 16 * 768) {
        (((it * 22695477 + 1) ushr 13) and 0xFFFF) / 65535f - 0.5f
    }

    private fun assertAt(
        actual: FloatArray,
        expected: List<Pair<Int, Float>>,
        name: String,
        tol: Float = 1e-5f,
    ) {
        for ((index, want) in expected) {
            val got = actual[index]
            assertTrue(
                kotlin.math.abs(want - got) <= tol,
                "$name[$index]: expected $want, got $got (tol $tol)",
            )
        }
    }

    @Test
    fun syntheticInputMatchesTheGeneratorTheGoldenValuesCameFrom() {
        // If this drifts, every expectation below is measuring the wrong input.
        val head = synthetic(8).map { it.toInt() }
        assertEquals(listOf(0, 198, 140, 83, 25, 223, 166, 108), head)
    }

    private data class Grid(
        val rows: Int,
        val cols: Int,
        val pixelValues: List<Pair<Int, Float>>,
        val cos: List<Pair<Int, Float>>,
        val sin: List<Pair<Int, Float>>,
        val posEmbeds: List<Pair<Int, Float>>,
    )

    private val grids = listOf(
        Grid(
            16, 16,
            pixelValues = listOf(0 to -1.0f, 28086 to -0.88235295f, 56173 to -0.26274508f, 84260 to -0.59215683f, 112346 to 0.05098045f, 140433 to -0.28627449f, 168520 to 0.34117651f, 196607 to 0.00392163f),
            cos = listOf(0 to 1.0f, 1462 to 0.91668332f, 2925 to 0.99999607f, 4388 to 0.69670671f, 5850 to 0.99981999f, 7313 to -0.62767971f, 8776 to 0.98384368f, 10239 to 0.99993724f),
            sin = listOf(0 to 0.0f, 1462 to 0.39961436f, 2925 to 0.00281170f, 4388 to 0.71735609f, 5850 to 0.01897253f, 7313 to 0.77847171f, 8776 to 0.17902957f, 10239 to 0.01120293f),
            posEmbeds = listOf(0 to -0.50000000f, 28086 to -0.20310521f, 56173 to 0.13607234f, 84260 to 0.47523463f, 112346 to -0.22787061f, 140433 to 0.11130691f, 168520 to 0.45046920f, 196607 to -0.21036851f),
        ),
        Grid(
            21, 12,
            pixelValues = listOf(0 to -1.0f, 28086 to -0.12156856f, 56173 to 0.60784328f, 84260 to -0.96862745f, 112346 to 0.21568632f, 140433 to 0.63921583f, 168520 to -0.63137257f, 196607 to 0.0f),
            cos = listOf(0 to 1.0f, 1462 to 0.95977265f, 2925 to 0.99999225f, 4388 to 0.45359612f, 5850 to 0.99997997f, 7313 to 1.0f, 8776 to 0.98384368f, 10239 to 0.99993724f),
            sin = listOf(0 to 0.0f, 1462 to 0.28077835f, 2925 to 0.00393638f, 4388 to 0.89120734f, 5850 to 0.00632451f, 7313 to 0.0f, 8776 to 0.17902957f, 10239 to 0.01120293f),
            posEmbeds = listOf(0 to -0.36016023f, 28086 to 0.09855140f, 56173 to 0.12112357f, 84260 to 0.08380001f, 112346 to 0.05837143f, 140433 to -0.06986260f, 168520 to -0.10651723f, 196607 to 0.06370947f),
        ),
        Grid(
            28, 9,
            pixelValues = listOf(0 to -1.0f, 28086 to 0.02745104f, 56173 to 0.41960788f, 84260 to 0.99215698f, 112346 to -0.16862738f, 140433 to 0.41176474f, 168520 to 0.80392170f, 196607 to 0.0f),
            cos = listOf(0 to 1.0f, 1462 to 1.0f, 2925 to 0.99998420f, 4388 to 0.07073720f, 5850 to 0.99997997f, 7313 to -0.11596616f, 8776 to 0.98384368f, 10239 to 0.99993724f),
            sin = listOf(0 to 0.0f, 1462 to 0.0f, 2925 to 0.00562338f, 4388 to 0.99749500f, 5850 to 0.00632451f, 7313 to 0.99325317f, 8776 to 0.17902957f, 10239 to 0.01120293f),
            posEmbeds = listOf(0 to -0.24315049f, 28086 to 0.13008440f, 56173 to 0.10335387f, 84260 to 0.06603031f, 112346 to -0.05235254f, 140433 to -0.07151493f, 168520 to -0.14386205f, 196607 to 0.11949667f),
        ),
        Grid(
            4, 64,
            pixelValues = listOf(0 to -1.0f, 28086 to -0.39607841f, 56173 to 0.77254915f, 84260 to 0.92156875f, 112346 to 0.53725505f, 140433 to 0.69411778f, 168520 to -0.14509797f, 196607 to 0.00392163f),
            cos = listOf(0 to 1.0f, 1462 to 0.14723410f, 2925 to 0.99999982f, 4388 to 0.98006660f, 5850 to 0.98545539f, 7313 to 0.17515653f, 8776 to 0.98384368f, 10239 to 0.99993724f),
            sin = listOf(0 to 0.0f, 1462 to 0.98910165f, 2925 to 0.00056234f, 4388 to 0.19866933f, 5850 to 0.16993430f, 7313 to 0.98454058f, 8776 to 0.17902957f, 10239 to 0.01120293f),
            posEmbeds = listOf(0 to 0.01025244f, 28086 to -0.10076813f, 56173 to 0.06468108f, 84260 to -0.09600405f, 112346 to -0.07070077f, 140433 to 0.09674411f, 168520 to -0.14294679f, 196607 to 0.10082017f),
        ),
        Grid(
            64, 4,
            pixelValues = listOf(0 to -1.0f, 28086 to -0.39607841f, 56173 to 0.77254915f, 84260 to 0.92156875f, 112346 to 0.53725505f, 140433 to 0.69411778f, 168520 to -0.14509797f, 196607 to 0.00392163f),
            cos = listOf(0 to 1.0f, 1462 to 0.99950004f, 2925 to 0.99992347f, 4388 to -0.96679819f, 5850 to 0.99997997f, 7313 to 1.0f, 8776 to 0.98384368f, 10239 to 0.99993724f),
            sin = listOf(0 to 0.0f, 1462 to 0.03161751f, 2925 to 0.01237119f, 4388 to -0.25554121f, 5850 to 0.00632451f, 7313 to 0.0f, 8776 to 0.17902957f, 10239 to 0.01120293f),
            posEmbeds = listOf(0 to 0.02519047f, 28086 to 0.11007150f, 56173 to -0.00064278f, 84260 to -0.03647478f, 112346 to -0.14585051f, 140433 to -0.07621649f, 168520 to 0.13729250f, 196607 to 0.08586960f),
        ),
    )

    @Test
    fun encoderInputMatchesTheReferenceAtEveryGrid() {
        for (g in grids) {
            val rgb = synthetic(g.rows * 16 * g.cols * 16 * 3)
            val pixels = HayaiHost.encoderInput(rgb, g.rows, g.cols)
            assertEquals(HayaiHost.MAX_PATCHES * 768, pixels.size, "${g.rows}x${g.cols} size")
            assertAt(pixels, g.pixelValues, "pixelValues ${g.rows}x${g.cols}")
        }
    }

    @Test
    fun ropeMatchesTheReferenceAtEveryGrid() {
        for (g in grids) {
            val (cos, sin) = HayaiHost.rope(g.rows, g.cols)
            assertAt(cos, g.cos, "cos ${g.rows}x${g.cols}")
            assertAt(sin, g.sin, "sin ${g.rows}x${g.cols}")
        }
    }

    @Test
    fun attentionMaskCountsTheGridAtEveryGrid() {
        for (g in grids) {
            val mask = HayaiHost.attentionMask(g.rows, g.cols)
            assertEquals(g.rows * g.cols, mask.count { it == 1 }, "${g.rows}x${g.cols} real patches")
        }
    }

    @Test
    fun positionEmbeddingsAreProducedForEveryGridAndPadWithTheFirstPatch() {
        val base = positionBase()
        for (g in grids) {
            val pos = HayaiHost.positionEmbeddings(base, g.rows, g.cols)
            assertEquals(HayaiHost.MAX_PATCHES * 768, pos.size, "${g.rows}x${g.cols} size")
            assertAt(pos, g.posEmbeds, "posEmbeds ${g.rows}x${g.cols}")
            val used = g.rows * g.cols
            for (slot in used until HayaiHost.MAX_PATCHES) {
                assertEquals(pos[0], pos[slot * 768], "${g.rows}x${g.cols} slot $slot padding")
            }
            assertTrue(pos.any { it != 0f }, "${g.rows}x${g.cols} must not be all zeros")
        }
    }

    @Test
    fun theWholeGridSetStaysWithinTheGraphBudget() {
        for (g in grids) {
            assertTrue(
                g.rows * g.cols <= HayaiHost.MAX_PATCHES,
                "${g.rows}x${g.cols} exceeds the patch budget",
            )
            assertEquals(g.rows to g.cols, HayaiHost.scaledSize(g.rows * 16, g.cols * 16).let {
                it.first / 16 to it.second / 16
            }, "scaledSize disagrees with the grid the reference chose for ${g.rows}x${g.cols}")
        }
    }
}
