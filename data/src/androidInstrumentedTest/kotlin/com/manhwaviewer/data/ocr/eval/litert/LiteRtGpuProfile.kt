package com.manhwaviewer.data.ocr.eval.litert

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import com.manhwaviewer.data.ocr.DetectedBox
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.eval.EvalDataset
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.hayai.HayaiHost
import com.manhwaviewer.data.ocr.hayai.HayaiModelStager
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.readRgbHwc
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Where the GPU re-export's time goes, graph by graph, with the host work taken out.
 *
 * Each graph is timed three ways on one real crop:
 * - latency: write inputs, run, read the output, every call — what the decode loop pays;
 * - pipelined: run N times without reading, then read once — the GPU's own time per call when it
 *   never waits on the host;
 * - with gaps (decode only): a CPU busy-wait after each step, standing in for the host's per-token
 *   work, to see whether short idle gaps cost clock speed.
 *
 * Phase boundaries are logged with `elapsedRealtime` so a host-side sampler of the GPU's clock,
 * load and temperature (read from `/sys/kernel/gpu`, same clock via `/proc/uptime`) lines up.
 */
class LiteRtGpuProfile {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()

    private class Model(val model: CompiledModel, inputs: Int, outputs: Int) {
        val ins = (0 until inputs).associate { "args_$it" to model.createInputBuffer("args_$it", SIG) }
        val outs = (0 until outputs).associate { "output_$it" to model.createOutputBuffer("output_$it", SIG) }
        fun input(i: Int) = ins.getValue("args_$i")
        fun output(i: Int) = outs.getValue("output_$i")
        fun run(feeds: Map<String, TensorBuffer> = ins, fetches: Map<String, TensorBuffer> = outs) = model.run(feeds, fetches, SIG)
    }

    private fun mark(phase: String, edge: String) =
        Log.i(TAG, "PHASE $phase $edge ${SystemClock.elapsedRealtime()}")

    private fun stats(ns: List<Long>): JSONObject {
        val sorted = ns.sorted()
        return JSONObject()
            .put("n", ns.size)
            .put("median_ms", sorted[sorted.size / 2] / 1e6)
            .put("p10_ms", sorted[sorted.size / 10] / 1e6)
            .put("p90_ms", sorted[sorted.size * 9 / 10] / 1e6)
            .put("mean_ms", ns.average() / 1e6)
    }

    /** Write / run / read split of [n] calls, each ending with a read that waits for the GPU. */
    private fun latency(name: String, n: Int, write: (Int) -> Unit, run: () -> Unit, read: () -> Unit, gapMs: Double = 0.0): JSONObject {
        val w = ArrayList<Long>(n)
        val r = ArrayList<Long>(n)
        val d = ArrayList<Long>(n)
        val total = ArrayList<Long>(n)
        mark(name, "start")
        repeat(n) { i ->
            val t0 = System.nanoTime()
            write(i)
            val t1 = System.nanoTime()
            run()
            val t2 = System.nanoTime()
            read()
            val t3 = System.nanoTime()
            w += t1 - t0; r += t2 - t1; d += t3 - t2; total += t3 - t0
            if (gapMs > 0) {
                val until = System.nanoTime() + (gapMs * 1e6).toLong()
                while (System.nanoTime() < until) Unit
            }
        }
        mark(name, "end")
        return JSONObject().put("write", stats(w)).put("run", stats(r)).put("read", stats(d)).put("total", stats(total))
    }

    /** [n] runs back to back and one read at the end: the GPU's time per call when never waited on. */
    private fun pipelined(name: String, n: Int, write: (Int) -> Unit, run: () -> Unit, read: () -> Unit): JSONObject {
        mark(name, "start")
        val t0 = System.nanoTime()
        repeat(n) { i ->
            write(i)
            run()
        }
        read()
        val per = (System.nanoTime() - t0) / n
        mark(name, "end")
        return JSONObject().put("n", n).put("per_call_ms", per / 1e6)
    }

    @Test
    fun profile() {
        val dataset = EvalDataset.loadOrSkip(context)
        val config = LiteRtConfig.fromArguments(args)
        val dir = File(File(context.filesDir, "ocr-eval"), "hayai-litert-gpu")
        val layout = HayaiLiteRtGraphs.Layout.GPU
        for (name in listOf(layout.encoder, layout.prefill, layout.decode)) {
            assumeTrue("Missing $name in $dir", File(dir, name).isFile)
        }
        val environment = Environment.create()
        fun compile(file: String) = CompiledModel.create(File(dir, file).absolutePath, config.options(), environment)
        val encoder = Model(compile(layout.encoder), 3, 1)
        val prefill = Model(compile(layout.prefill), 5, 1 + CACHES)
        val decode = Model(compile(layout.decode), 5 + CACHES, 1 + CACHES)

        // One real crop through the host arithmetic the app uses.
        val hostDir = HayaiModelStager.stage(context.assets::open, File(context.cacheDir, HayaiModelStager.ASSET_DIR), HayaiEngineFactory.MODEL_VERSION)
        val baseBytes = File(hostDir, "position_base.f32").readBytes()
        val positionBase = FloatArray(baseBytes.size / 4).also { ByteBuffer.wrap(baseBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
        val page = dataset.pages.first()
        val image = decodeImage(page.imageFile.readBytes())
        val item = page.items.first()
        val padded = YoloPostProcessor.addPadding(DetectedBox(item.x, item.y, item.width, item.height, confidence = 1f, isJapanese = true), image.width, image.height)
        val crop = cropToPlatformImage(image, padded.x, padded.y, padded.width, padded.height)
        val (rows, cols) = HayaiHost.scaledSize(crop.height, crop.width)
        val resized = HayaiHost.resizeRgb(readRgbHwc(crop), crop.height, crop.width, rows, cols)
        val pr = rows / HayaiHost.PATCH_SIZE
        val pc = cols / HayaiHost.PATCH_SIZE
        val pixels = HayaiHost.encoderInput(FloatArray(resized.size) { resized[it].toFloat() }, pr, pc)
        val mask = HayaiHost.attentionMask(pr, pc).let { m -> FloatArray(m.size) { if (m[it] == 0) HayaiHost.NEG_INF else 0f } }
        val pos = HayaiHost.positionEmbeddings(positionBase, pr, pc)
        val (cos, sin) = HayaiHost.rope(pr, pc)
        val prefillMask = HayaiHost.prefillMask()

        val writeEncoder: (Int) -> Unit = {
            encoder.input(0).writeFloat(pixels); encoder.input(1).writeFloat(mask); encoder.input(2).writeFloat(pos)
        }
        writeEncoder(0); encoder.run()
        val visual = encoder.output(0).readFloat()
        val writePrefill: (Int) -> Unit = {
            prefill.input(0).writeFloat(visual); prefill.input(1).writeInt(intArrayOf(1))
            prefill.input(2).writeFloat(cos); prefill.input(3).writeFloat(sin); prefill.input(4).writeFloat(prefillMask)
        }
        writePrefill(0); prefill.run()
        val sets = arrayOf(List(CACHES) { decode.input(5 + it) }, List(CACHES) { decode.output(1 + it) })
        for (i in 0 until CACHES) sets[0][i].writeFloat(prefill.output(1 + i).readFloat())
        var current = 0
        fun decodeStep(i: Int) {
            val p = HayaiHost.MAX_PATCHES + 1 + (i % 60)
            decode.input(0).writeInt(intArrayOf(100 + i % 50))
            decode.input(1).writeFloat(cos.copyOfRange(p * HayaiHost.D_AXIS, (p + 1) * HayaiHost.D_AXIS))
            decode.input(2).writeFloat(sin.copyOfRange(p * HayaiHost.D_AXIS, (p + 1) * HayaiHost.D_AXIS))
            decode.input(3).writeFloat(HayaiHost.decodeMask(p))
            decode.input(4).writeFloat(HayaiHost.writeMask(p))
        }
        fun decodeRun() {
            val next = 1 - current
            val feeds = HashMap<String, TensorBuffer>()
            for (k in 0 until 5) feeds["args_$k"] = decode.input(k)
            val fetches = HashMap<String, TensorBuffer>()
            fetches["output_0"] = decode.output(0)
            for (k in 0 until CACHES) {
                feeds["args_${5 + k}"] = sets[current][k]
                fetches["output_${1 + k}"] = sets[next][k]
            }
            decode.run(feeds, fetches)
            current = next
        }
        val readLogits = { decode.output(0).readFloat(); Unit }

        // Warm-up so compile-time lazy work does not land in the first sample.
        repeat(3) { writeEncoder(0); encoder.run(); encoder.output(0).readFloat() }
        repeat(3) { writePrefill(0); prefill.run(); prefill.output(0).readFloat() }
        repeat(5) { decodeStep(it); decodeRun(); readLogits() }

        val report = JSONObject().put("config", config.label)
        report.put("encoder_latency", latency("encoder_latency", 20, writeEncoder, { encoder.run() }, { encoder.output(0).readFloat() }))
        report.put("encoder_pipelined", pipelined("encoder_pipelined", 20, writeEncoder, { encoder.run() }, { encoder.output(0).readFloat() }))
        report.put("prefill_latency", latency("prefill_latency", 20, writePrefill, { prefill.run() }, { prefill.output(0).readFloat() }))
        report.put("prefill_pipelined", pipelined("prefill_pipelined", 20, writePrefill, { prefill.run() }, { prefill.output(0).readFloat() }))
        report.put("decode_latency", latency("decode_latency", 120, ::decodeStep, ::decodeRun, readLogits))
        report.put("decode_pipelined", pipelined("decode_pipelined", 120, ::decodeStep, ::decodeRun, readLogits))
        val gaps = JSONArray()
        for (gap in listOf(0.0, 2.0, 5.0, 10.0, 0.0)) {
            gaps.put(latency("decode_gap_$gap", 80, ::decodeStep, ::decodeRun, readLogits, gapMs = gap).put("gap_ms", gap))
        }
        report.put("decode_gaps", gaps)
        Log.i(TAG, report.toString())
        dataset.writeOutput("litert-gpu-profile-${config.label}.json", report)
        for (m in listOf(encoder, prefill, decode)) {
            (m.ins.values + m.outs.values).forEach { it.close() }
            m.model.close()
        }
        environment.close()
    }

    private companion object {
        const val TAG = "LITERT_PROFILE"
        const val SIG = "serving_default"
        const val CACHES = 24
    }
}
