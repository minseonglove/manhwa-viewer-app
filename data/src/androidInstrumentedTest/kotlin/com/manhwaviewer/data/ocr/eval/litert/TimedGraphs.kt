package com.manhwaviewer.data.ocr.eval.litert

import com.manhwaviewer.data.ocr.hayai.HayaiGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiKvCache
import com.manhwaviewer.data.ocr.hayai.HayaiStepResult
import com.manhwaviewer.data.ocr.hayai.HayaiVisualFeatures
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * Per-graph time around any [HayaiGraphs], so ORT and LiteRT can be compared graph by graph and
 * not only per recognition.
 *
 * Atomic because the ORT engine is driven four recognitions at a time in the app's repository;
 * under that concurrency the sums are compute time, not wall time.
 */
class TimedGraphs(private val delegate: HayaiGraphs) : HayaiGraphs {

    private val encodeNs = AtomicLong()
    private val prefillNs = AtomicLong()
    private val decodeNs = AtomicLong()
    private val encodes = AtomicLong()
    private val decodes = AtomicLong()

    override fun encode(pixelValues: FloatArray, attentionMask: IntArray, posEmbeds: FloatArray): HayaiVisualFeatures {
        val start = System.nanoTime()
        return delegate.encode(pixelValues, attentionMask, posEmbeds).also {
            encodeNs.addAndGet(System.nanoTime() - start)
            encodes.incrementAndGet()
        }
    }

    override fun prefill(visual: HayaiVisualFeatures, tokenId: Int, cos: FloatArray, sin: FloatArray, mask: FloatArray): HayaiStepResult {
        val start = System.nanoTime()
        return delegate.prefill(visual, tokenId, cos, sin, mask).also { prefillNs.addAndGet(System.nanoTime() - start) }
    }

    override fun decodeStep(
        tokenId: Int,
        cos: FloatArray,
        sin: FloatArray,
        mask: FloatArray,
        pos: Int,
        cache: HayaiKvCache,
    ): HayaiStepResult {
        val start = System.nanoTime()
        return delegate.decodeStep(tokenId, cos, sin, mask, pos, cache).also {
            decodeNs.addAndGet(System.nanoTime() - start)
            decodes.incrementAndGet()
        }
    }

    override fun close() = delegate.close()

    fun reset() {
        listOf(encodeNs, prefillNs, decodeNs, encodes, decodes).forEach { it.set(0) }
    }

    /** Mean milliseconds per call of each graph, plus how many calls the means cover. */
    fun snapshot(): JSONObject {
        val n = encodes.get().coerceAtLeast(1)
        val d = decodes.get().coerceAtLeast(1)
        return JSONObject()
            .put("recognitions", encodes.get())
            .put("decode_steps", decodes.get())
            .put("encode_ms", encodeNs.get() / 1e6 / n)
            .put("prefill_ms", prefillNs.get() / 1e6 / n)
            .put("decode_step_ms", decodeNs.get() / 1e6 / d)
            .put("graphs_ms_per_recognition", (encodeNs.get() + prefillNs.get() + decodeNs.get()) / 1e6 / n)
    }
}
