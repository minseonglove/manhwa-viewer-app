package com.manhwaviewer.data.ocr.hayai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The adapter between the app's [com.manhwaviewer.data.ocr.MangaOcrEngine] contract and Hayai.
 *
 * What is under test is the crop-handling step the app path adds on top of the decode loop:
 * choosing the patch grid for this crop's aspect ratio and resizing to it. Both are silent
 * failures — a swapped row/column or a skipped resize still produces text, just worse text —
 * so the fake graphs capture what actually reached the encoder and the assertions compare it
 * against [HayaiHost]'s own output for the same crop.
 */
class HayaiMangaOcrEngineTest {

    private object Visual : HayaiVisualFeatures {
        override fun close() = Unit
    }
    private object Cache : HayaiKvCache {
        override fun close() = Unit
    }

    private val BOS = 2
    private val EOS = 3
    private val PAD = 0
    private val VOCAB = 8

    private class CapturingGraphs : HayaiGraphs {
        var pixelValues: FloatArray? = null
        var attentionMask: IntArray? = null
        var posEmbeds: FloatArray? = null
        var closed = false

        override fun encode(pixelValues: FloatArray, attentionMask: IntArray, posEmbeds: FloatArray):
            HayaiVisualFeatures {
            this.pixelValues = pixelValues
            this.attentionMask = attentionMask
            this.posEmbeds = posEmbeds
            return Visual
        }

        override fun prefill(
            visual: HayaiVisualFeatures, tokenId: Int,
            cos: FloatArray, sin: FloatArray, mask: FloatArray,
        ): HayaiStepResult = HayaiStepResult(FloatArray(8) { if (it == 3) 10f else 0f }, Cache)

        override fun decodeStep(
            tokenId: Int, cos: FloatArray, sin: FloatArray,
            mask: FloatArray, pos: Int, cache: HayaiKvCache,
        ): HayaiStepResult = HayaiStepResult(FloatArray(8) { if (it == 3) 10f else 0f }, Cache)

        override fun close() { closed = true }
    }

    /**
     * A non-constant position base. Zeros would make [HayaiHost.positionEmbeddings] output the
     * same thing for every grid, and the grid is what this test is about.
     */
    private val positionBase = FloatArray(16 * 16 * 768) { (it % 251).toFloat() / 251f }

    private fun adapter(graphs: HayaiGraphs) = HayaiMangaOcrEngine(
        HayaiOcrEngine(
            graphs = graphs,
            tokenizer = HayaiTokenizer(mapOf(BOS to "<bos>", EOS to "<eos>", PAD to "<pad>"), setOf(PAD, BOS, EOS)),
            positionBase = positionBase,
            bosId = BOS, eosId = EOS, padId = PAD,
        )
    )

    /** A deterministic crop; the values only have to survive the resize unchanged in kind. */
    private fun crop(height: Int, width: Int): IntArray {
        var state = 0x9E3779B9.toInt()
        return IntArray(height * width * 3) {
            state = state * 1664525 + 1013904223
            (state ushr 24) and 0xFF
        }
    }

    @Test
    fun choosesThePatchGridHayaiHostWouldChooseForTheCropShape() {
        // 380x140 is a tall vertical bubble — the shape this OCR path actually receives, and the
        // one where a rows/cols swap survives every shape check.
        val height = 380
        val width = 140
        val graphs = CapturingGraphs()
        adapter(graphs).recognizeRgb(crop(height, width), height, width)

        val (targetHeight, targetWidth) = HayaiHost.scaledSize(height, width)
        val rows = targetHeight / HayaiHost.PATCH_SIZE
        val cols = targetWidth / HayaiHost.PATCH_SIZE
        assertTrue(rows != cols, "the fixture must be non-square or the swap cannot be detected")

        // The mask only pins the patch COUNT: rows*cols is the same either way round, so it
        // cannot see a swap. The position embeddings are resampled to the grid itself and can.
        assertEquals(
            HayaiHost.attentionMask(rows, cols).toList(),
            graphs.attentionMask!!.toList(),
        )
        assertEquals(
            HayaiHost.positionEmbeddings(positionBase, rows, cols).toList(),
            graphs.posEmbeds!!.toList(),
        )
    }

    @Test
    fun feedsTheEncoderTheResizedCropRatherThanTheOriginal() {
        val height = 380
        val width = 140
        val rgb = crop(height, width)
        val graphs = CapturingGraphs()
        adapter(graphs).recognizeRgb(rgb, height, width)

        val (targetHeight, targetWidth) = HayaiHost.scaledSize(height, width)
        val resized = HayaiHost.resizeRgb(rgb, height, width, targetHeight, targetWidth)
        val expected = HayaiHost.encoderInput(
            FloatArray(resized.size) { resized[it].toFloat() },
            targetHeight / HayaiHost.PATCH_SIZE,
            targetWidth / HayaiHost.PATCH_SIZE,
        )
        assertEquals(expected.size, graphs.pixelValues!!.size)
        assertEquals(expected.toList(), graphs.pixelValues!!.toList())
    }

    @Test
    fun handlesACropAlreadyAtPatchSizeWithoutResizing() {
        val height = 256
        val width = 256
        val rgb = crop(height, width)
        val graphs = CapturingGraphs()
        adapter(graphs).recognizeRgb(rgb, height, width)

        assertEquals(256 to 256, HayaiHost.scaledSize(height, width))
        val expected = HayaiHost.encoderInput(FloatArray(rgb.size) { rgb[it].toFloat() }, 16, 16)
        assertEquals(expected.toList(), graphs.pixelValues!!.toList())
    }

    @Test
    fun closingTheAdapterClosesTheGraphs() {
        val graphs = CapturingGraphs()
        adapter(graphs).close()
        assertTrue(graphs.closed)
    }
}
