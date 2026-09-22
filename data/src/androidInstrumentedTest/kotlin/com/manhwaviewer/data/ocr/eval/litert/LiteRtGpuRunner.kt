package com.manhwaviewer.data.ocr.eval.litert

import ai.onnxruntime.OrtEnvironment
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.DetectedBox
import com.manhwaviewer.data.ocr.DetectedRegion
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.TextDetector
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.eval.EvalDataset
import com.manhwaviewer.data.ocr.eval.OnnxYoloRunner
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.hayai.HayaiGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiHost
import com.manhwaviewer.data.ocr.hayai.HayaiMangaOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiModelStager
import com.manhwaviewer.data.ocr.hayai.HayaiOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiTokenizer
import com.manhwaviewer.data.ocr.image.PlatformImage
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.readRgbHwc
import com.manhwaviewer.data.ocr.inference.YoloRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The OCR pipeline on LiteRT (GPU by default) against the app's ONNX Runtime CPU path.
 *
 * Three steps, each a gate for the next:
 * - [probe]: do the graphs compile and run on this GPU, does the KV swap work, does LiteRT read
 *   the same text and find the same boxes as ORT on a sample, and does the injected detector
 *   reproduce the app's detector exactly.
 * - [recognition]: all ground-truth crops through the LiteRT engine, written in the scorer's shape.
 * - [pairedPages]: page time through the app's repository, both pipelines open at once and the
 *   order alternated per page.
 *
 * LiteRT models are pushed to `filesDir/ocr-eval/hayai-litert` and `filesDir/ocr-eval/yolo-litert`;
 * the tokenizer and position table come from the app's own staged assets (byte-identical to the
 * LiteRT export's).
 */
class LiteRtGpuRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val evalRoot = File(context.filesDir, "ocr-eval")

    /** `-e hayaiLayout gpu|author`: the GPU re-export (default) or the author's int8 export. */
    private fun layout(): HayaiLiteRtGraphs.Layout = when (args.getString("hayaiLayout") ?: "gpu") {
        "gpu" -> HayaiLiteRtGraphs.Layout.GPU
        "author" -> HayaiLiteRtGraphs.Layout.AUTHOR
        else -> error("hayaiLayout is gpu or author")
    }

    private fun litertDir(): File = File(
        evalRoot,
        if (layout() == HayaiLiteRtGraphs.Layout.GPU) "hayai-litert-gpu" else "hayai-litert",
    ).also { dir ->
        for (name in listOf(layout().encoder, layout().prefill, layout().decode)) {
            assumeTrue("Missing $name in ${dir.absolutePath}", File(dir, name).isFile)
        }
    }

    /** The all-ORT Hayai set the app used to ship, pushed for baseline runs (the APK no longer carries its encoder). */
    private fun onnxBaselineDir(): File = File(evalRoot, "hayai-onnx").also {
        assumeTrue("No ONNX baseline at ${it.absolutePath}", File(it, "hayai_encoder.onnx").isFile)
    }

    private fun yoloFile(): File = File(File(evalRoot, "yolo-litert"), LiteRtYoloRunner.FILE).also {
        assumeTrue("Missing ${it.absolutePath}", it.isFile)
    }

    private fun stagedOnnxDir(): File = HayaiModelStager.stage(
        context.assets::open,
        File(context.cacheDir, HayaiModelStager.ASSET_DIR),
        HayaiEngineFactory.MODEL_VERSION,
    )

    private fun kvTransfer(): HayaiLiteRtGraphs.KvTransfer = when (args.getString("kv") ?: "copy") {
        "copy" -> HayaiLiteRtGraphs.KvTransfer.COPY
        "swap" -> HayaiLiteRtGraphs.KvTransfer.SWAP
        else -> error("kv is copy or swap")
    }

    /** The same assembly as `HayaiEngineFactory.fromDirectory`, with the graphs swappable. */
    private fun engine(graphs: HayaiGraphs, hostDir: File): HayaiMangaOcrEngine {
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(hostDir, "tokenizer.json").readText())
        fun id(token: String) = tokenizer.tokenIdOf(token) ?: error("tokenizer.json carries no $token")
        val bytes = File(hostDir, "position_base.f32").readBytes()
        val positionBase = FloatArray(bytes.size / 4).also {
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
        }
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = graphs,
                tokenizer = tokenizer,
                positionBase = positionBase,
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    private fun runtimeInfo(environment: Environment, config: LiteRtConfig): JSONObject = JSONObject()
        .put("litert_maven", LITERT_VERSION)
        .put("config", config.label)
        .put("available_accelerators", environment.getAvailableAccelerators().joinToString(",") { it.name })

    private class Crop(val key: String, val rgb: IntArray, val height: Int, val width: Int)

    private fun crops(dataset: EvalDataset, limit: Int = Int.MAX_VALUE): List<Crop> {
        val out = mutableListOf<Crop>()
        for (page in dataset.pages) {
            val image = decodeImage(page.imageFile.readBytes())
            for (item in page.items) {
                if (out.size >= limit) return out
                val padded = YoloPostProcessor.addPadding(
                    DetectedBox(item.x, item.y, item.width, item.height, confidence = 1f, isJapanese = true),
                    image.width,
                    image.height,
                )
                val crop = cropToPlatformImage(image, padded.x, padded.y, padded.width, padded.height)
                out += Crop("${page.name}/${item.id}", readRgbHwc(crop), crop.height, crop.width)
            }
        }
        return out
    }

    private fun iou(a: DetectedRegion, b: DetectedRegion): Double {
        val w = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
        val h = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
        if (w <= 0 || h <= 0) return 0.0
        val inter = w.toDouble() * h
        return inter / (a.width.toDouble() * a.height + b.width.toDouble() * b.height - inter)
    }

    /** Regions of [got] matched to [ref] greedily by IoU; the worst match and what was left over. */
    private fun compareRegions(ref: List<DetectedRegion>, got: List<DetectedRegion>): JSONObject {
        val used = mutableSetOf<Int>()
        var minIou = 1.0
        var missing = 0
        for (r in ref) {
            val best = got.indices.filter { it !in used }.maxByOrNull { iou(r, got[it]) }
            if (best == null || iou(r, got[best]) < 0.5) {
                missing++
                continue
            }
            used += best
            minIou = min(minIou, iou(r, got[best]))
        }
        return JSONObject()
            .put("ref", ref.size).put("got", got.size)
            .put("missing", missing).put("extra", got.size - used.size)
            .put("min_iou", if (used.isEmpty()) JSONObject.NULL else minIou)
    }

    /**
     * Compiles each model alone under every GPU setting worth trying and records which succeed,
     * how long compilation took, and the error otherwise. No inference: this only answers
     * "can this GPU take this graph at all, and under which options".
     */
    @Test
    fun compileMatrix() {
        val environment = Environment.create()
        val models = linkedMapOf(
            "encoder" to File(litertDir(), layout().encoder),
            "prefill" to File(litertDir(), layout().prefill),
            "decode" to File(litertDir(), layout().decode),
            "yolo" to yoloFile(),
        )
        // `-e models yolo,decode` limits the run; a native crash in one model otherwise ends the whole matrix.
        args.getString("models")?.split(",")?.toSet()?.let { keep -> models.keys.retainAll(keep) }
        val configs = buildList {
            for (backend in listOf(CompiledModel.GpuOptions.Backend.OPENCL, CompiledModel.GpuOptions.Backend.OPENGL)) {
                for (precision in listOf(CompiledModel.GpuOptions.Precision.FP16, CompiledModel.GpuOptions.Precision.FP32)) {
                    for (storage in listOf(CompiledModel.GpuOptions.BufferStorageType.DEFAULT, CompiledModel.GpuOptions.BufferStorageType.BUFFER)) {
                        for (accelerators in listOf(setOf(Accelerator.GPU), setOf(Accelerator.GPU, Accelerator.CPU))) {
                            add(LiteRtConfig(accelerators, precision, backend, storage))
                        }
                    }
                }
            }
            add(LiteRtConfig(accelerators = setOf(Accelerator.CPU)))
        }.filter { config -> args.getString("configFilter")?.let { it in config.label } ?: true }
        val rows = JSONArray()
        for ((name, file) in models) {
            for (config in configs) {
                val row = JSONObject().put("model", name).put("config", config.label)
                val start = SystemClock.elapsedRealtime()
                try {
                    CompiledModel.create(file.absolutePath, config.options(), environment).close()
                    row.put("ok", true)
                } catch (e: Exception) {
                    row.put("ok", false).put("error", e.message)
                }
                row.put("ms", SystemClock.elapsedRealtime() - start)
                Log.i(TAG, row.toString())
                rows.put(row)
            }
        }
        environment.close()
        EvalDataset.loadOrSkip(context).writeOutput("litert-compile-matrix.json", JSONObject().put("rows", rows))
    }

    /**
     * The encoder alone on the GPU and on the CPU, same inputs, outputs compared element-wise.
     * The GPU output is read at once and again after a pause: if only the late read matches, the
     * run returned before the GPU finished and reads are racing it.
     */
    @Test
    fun encoderCheck() {
        val dataset = EvalDataset.loadOrSkip(context)
        val gpuConfig = LiteRtConfig.fromArguments(args)
        val environment = Environment.create()
        val hostDir = stagedOnnxDir()
        val bytes = File(hostDir, "position_base.f32").readBytes()
        val positionBase = FloatArray(bytes.size / 4).also {
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it)
        }
        val crop = crops(dataset, limit = 1).first()
        val (rows, cols) = HayaiHost.scaledSize(crop.height, crop.width)
        val resized = HayaiHost.resizeRgb(crop.rgb, crop.height, crop.width, rows, cols)
        val patchRows = rows / HayaiHost.PATCH_SIZE
        val patchCols = cols / HayaiHost.PATCH_SIZE
        val pixels = HayaiHost.encoderInput(FloatArray(resized.size) { resized[it].toFloat() }, patchRows, patchCols)
        val mask = HayaiHost.attentionMask(patchRows, patchCols)
        val pos = HayaiHost.positionEmbeddings(positionBase, patchRows, patchCols)

        fun encode(config: LiteRtConfig, pauseMs: Long): Pair<FloatArray, FloatArray> {
            val model = CompiledModel.create(File(litertDir(), layout().encoder).absolutePath, config.options(), environment)
            val inputs = (0..2).associate { "args_$it" to model.createInputBuffer("args_$it", "serving_default") }
            val output = model.createOutputBuffer("output_0", "serving_default")
            inputs.getValue("args_0").writeFloat(pixels)
            when (layout()) {
                HayaiLiteRtGraphs.Layout.AUTHOR -> inputs.getValue("args_1").writeLong(LongArray(mask.size) { mask[it].toLong() })
                HayaiLiteRtGraphs.Layout.GPU -> inputs.getValue("args_1").writeFloat(
                    FloatArray(mask.size) { if (mask[it] == 0) HayaiHost.NEG_INF else 0f },
                )
            }
            inputs.getValue("args_2").writeFloat(pos)
            model.run(inputs, mapOf("output_0" to output), "serving_default")
            val early = output.readFloat()
            Thread.sleep(pauseMs)
            val late = output.readFloat()
            Log.i(TAG, "${config.label} output types " + model.getOutputBufferRequirements("output_0", "serving_default").supportedTypes)
            inputs.values.forEach { it.close() }
            output.close()
            model.close()
            return early to late
        }

        fun diff(a: FloatArray, b: FloatArray): JSONObject {
            var maxAbs = 0.0
            var sumAbs = 0.0
            var nonFinite = 0
            for (i in a.indices) {
                if (!a[i].isFinite()) nonFinite++
                val d = abs(a[i].toDouble() - b[i].toDouble())
                if (d.isFinite()) { maxAbs = max(maxAbs, d); sumAbs += d }
            }
            return JSONObject().put("max_abs", maxAbs).put("mean_abs", sumAbs / a.size).put("non_finite", nonFinite)
        }

        val (cpu, _) = encode(LiteRtConfig(accelerators = setOf(Accelerator.CPU)), 0)
        val (gpuEarly, gpuLate) = encode(gpuConfig, 500)
        var cpuAbs = 0.0
        for (v in cpu) cpuAbs += abs(v.toDouble())
        val valid = patchRows * patchCols * 768
        val report = JSONObject()
            .put("config", gpuConfig.label)
            .put("patches", patchRows * patchCols)
            .put("cpu_mean_abs", cpuAbs / cpu.size)
            .put("gpu_early_vs_cpu", diff(gpuEarly, cpu))
            .put("gpu_late_vs_cpu", diff(gpuLate, cpu))
            .put("gpu_early_vs_cpu_valid_patches", diff(gpuEarly.copyOf(valid), cpu.copyOf(valid)))
            .put("gpu_early_vs_late", diff(gpuEarly, gpuLate))
        Log.i(TAG, report.toString())
        environment.close()
        dataset.writeOutput("litert-encoder-check-${gpuConfig.label}.json", report)
    }

    @Test
    fun probe() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val config = LiteRtConfig.fromArguments(args)
        val environment = Environment.create()
        val report = JSONObject().put("runtime", runtimeInfo(environment, config))

        val hostDir = stagedOnnxDir()
        val decoderConfig = LiteRtConfig.decoderFromArguments(args)
        report.put("decoder_config", decoderConfig.label).put("layout", layout().name)
        val liteGraphs = HayaiLiteRtGraphs(litertDir(), environment, layout(), config, decoderConfig)
        report.put("hayai_compile_ms", JSONObject(liteGraphs.compileMs))
        report.put("kv_swap", liteGraphs.probeSwap())
        val lite = TimedGraphs(liteGraphs)
        val onnx = TimedGraphs(HayaiOnnxGraphs(onnxBaselineDir()).apply { initialize() })
        val liteEngine = engine(lite, hostDir)
        val onnxEngine = engine(onnx, hostDir)

        val sample = crops(dataset, limit = args.getString("probeItems")?.toInt() ?: 40)
        for (engine in listOf(onnxEngine, liteEngine)) repeat(2) { engine.recognizeRgb(sample[0].rgb, sample[0].height, sample[0].width) }
        lite.reset(); onnx.reset()

        fun pass(engine: HayaiMangaOcrEngine): Pair<List<String>, List<Long>> {
            val texts = mutableListOf<String>()
            val times = mutableListOf<Long>()
            for (c in sample) {
                val start = SystemClock.elapsedRealtime()
                texts += engine.recognizeRgb(c.rgb, c.height, c.width)
                times += SystemClock.elapsedRealtime() - start
            }
            return texts to times
        }

        val (onnxTexts, onnxTimes) = pass(onnxEngine)
        val (copyTexts, copyTimes) = pass(liteEngine)
        val recognition = JSONObject()
            .put("items", sample.size)
            .put("onnx_cpu", JSONObject().put("median_ms", onnxTimes.sorted()[onnxTimes.size / 2]).put("graphs", onnx.snapshot()))
            .put("litert_copy", JSONObject().put("median_ms", copyTimes.sorted()[copyTimes.size / 2]).put("graphs", lite.snapshot())
                .put("same_text_as_onnx", sample.indices.count { onnxTexts[it] == copyTexts[it] }))
        if (report.getJSONObject("kv_swap").optBoolean("swap_runs") &&
            report.getJSONObject("kv_swap").optDouble("swap_max_abs_diff", 1.0) == 0.0
        ) {
            liteGraphs.kvTransfer = HayaiLiteRtGraphs.KvTransfer.SWAP
            lite.reset()
            val (swapTexts, swapTimes) = pass(liteEngine)
            recognition.put("litert_swap", JSONObject().put("median_ms", swapTimes.sorted()[swapTimes.size / 2]).put("graphs", lite.snapshot())
                .put("same_text_as_onnx", sample.indices.count { onnxTexts[it] == swapTexts[it] })
                .put("same_text_as_copy", sample.indices.count { copyTexts[it] == swapTexts[it] }))
        }
        recognition.put("differing", JSONArray().apply {
            for (i in sample.indices) if (onnxTexts[i] != copyTexts[i]) {
                put(JSONObject().put("key", sample[i].key).put("onnx", onnxTexts[i]).put("litert", copyTexts[i]))
            }
        })
        report.put("recognition", recognition)
        liteEngine.close()
        onnxEngine.close()

        val onnxYolo = OnnxYoloRunner(OnnxYoloRunner.pushedFile(context))
        val appYolo = YoloRunner(context)
        val appDetector = CommonYoloTextDetector(appYolo)
        val liteYolo = LiteRtYoloRunner(yoloFile(), environment, config)
        var onnxYoloNs = 0L
        var liteYoloNs = 0L
        val mirror = CommonYoloTextDetector({ x -> appYolo.run(x) })
        val liteDetector = CommonYoloTextDetector({ x ->
            val start = System.nanoTime()
            liteYolo.run(x).also { liteYoloNs += System.nanoTime() - start }
        })
        val timedOnnx = CommonYoloTextDetector({ x ->
            val start = System.nanoTime()
            onnxYolo.run(x).also { onnxYoloNs += System.nanoTime() - start }
        })
        val first = dataset.pages.first().imageFile.readBytes()
        appDetector.detect(first) // opens the app's detector
        repeat(2) { timedOnnx.detect(first); liteDetector.detect(first) }
        onnxYoloNs = 0; liteYoloNs = 0
        var mirrorIdentical = 0
        val pages = JSONArray()
        for (page in dataset.pages) {
            val bytes = page.imageFile.readBytes()
            val app = appDetector.detect(bytes)
            val mirrored = mirror.detect(bytes)
            if (app.map { listOf(it.x, it.y, it.width, it.height) } == mirrored.map { listOf(it.x, it.y, it.width, it.height) }) mirrorIdentical++
            val onnxRegions = timedOnnx.detect(bytes)
            val liteRegions = liteDetector.detect(bytes)
            pages.put(compareRegions(onnxRegions, liteRegions).put("page", page.name))
        }
        report.put("detection", JSONObject()
            .put("yolo_compile_ms", liteYolo.compileMs)
            .put("pages", dataset.pages.size)
            .put("injected_detector_identical_to_app", mirrorIdentical)
            .put("onnx_cpu_net_ms", onnxYoloNs / 1e6 / dataset.pages.size)
            .put("litert_net_ms", liteYoloNs / 1e6 / dataset.pages.size)
            .put("per_page", pages))
        liteYolo.close()
        onnxYolo.close()
        appYolo.close()
        environment.close()

        dataset.writeOutput("litert-probe-${config.label}.json", report)
    }

    @Test
    fun recognition() {
        val dataset = EvalDataset.loadOrSkip(context)
        val config = LiteRtConfig.fromArguments(args)
        val environment = Environment.create()
        val hostDir = stagedOnnxDir()
        val decoderConfig = LiteRtConfig.decoderFromArguments(args)
        val graphs = HayaiLiteRtGraphs(litertDir(), environment, layout(), config, decoderConfig, kvTransfer())
        val engine = engine(graphs, hostDir)
        val results = JSONObject()
        try {
            for (c in crops(dataset)) {
                val start = SystemClock.elapsedRealtime()
                val text = engine.recognizeRgb(c.rgb, c.height, c.width)
                results.put(c.key, JSONObject().put("text", text).put("ms", SystemClock.elapsedRealtime() - start))
            }
        } finally {
            engine.close()
        }
        dataset.writeOutput("recognition.json", results)
        dataset.writeOutput(
            "runtime.json",
            runtimeInfo(environment, config)
                .put("decoder_config", decoderConfig.label)
                .put("kv_transfer", graphs.kvTransfer.name)
                .put("compile_ms", JSONObject(graphs.compileMs)),
        )
        environment.close()
        assertEquals(dataset.itemCount, results.length())
    }

    private class StageClock {
        @Volatile var detectMs = 0L
        val spans: MutableList<LongArray> = Collections.synchronizedList(mutableListOf())

        fun reset() {
            detectMs = 0
            spans.clear()
        }
    }

    private class TimedDetector(private val delegate: TextDetector, private val clock: StageClock) : TextDetector {
        override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
            val start = SystemClock.elapsedRealtime()
            return delegate.detect(imageBytes).also { clock.detectMs = SystemClock.elapsedRealtime() - start }
        }
    }

    private class TimedOcr(private val delegate: MangaOcrEngine, private val clock: StageClock) : MangaOcrEngine {
        override suspend fun recognizeText(imageBytes: ByteArray): String = delegate.recognizeText(imageBytes)

        override suspend fun recognizeText(image: PlatformImage): String {
            val start = SystemClock.elapsedRealtime()
            return delegate.recognizeText(image).also { clock.spans.add(longArrayOf(start, SystemClock.elapsedRealtime())) }
        }

        override fun close() = delegate.close()
    }

    /**
     * Page time of the app's pipeline (ORT CPU detector + ORT CPU Hayai, four recognitions at a time)
     * against the LiteRT one (detector + Hayai on LiteRT, one recognition at a time), both open at
     * once and alternating which goes first per page, so they share thermal state.
     */
    @Test
    fun pairedPages() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val config = LiteRtConfig.fromArguments(args)
        val hostDir = stagedOnnxDir()
        // LiteRT objects are created and driven on one thread: the OpenGL backend is bound to the
        // thread that made its context, and a GPU engine in the app would own a thread anyway.
        val gpuExecutor = Executors.newSingleThreadExecutor()
        val gpu = gpuExecutor.asCoroutineDispatcher()
        val environment = withContext(gpu) { Environment.create() }
        val decoderConfig = LiteRtConfig.decoderFromArguments(args)
        val liteHayai = withContext(gpu) { HayaiLiteRtGraphs(litertDir(), environment, layout(), config, decoderConfig, kvTransfer()) }
        val liteYolo = withContext(gpu) { LiteRtYoloRunner(yoloFile(), environment, config) }
        val liteGraphs = TimedGraphs(liteHayai)
        val onnxGraphs = TimedGraphs(HayaiOnnxGraphs(onnxBaselineDir()).apply { initialize() })
        val onnxYolo = OnnxYoloRunner(OnnxYoloRunner.pushedFile(context))

        val clocks = mapOf("onnx" to StageClock(), "litert" to StageClock())
        val repos = linkedMapOf(
            "onnx" to TextDetectionRepositoryImpl(
                TimedDetector(CommonYoloTextDetector(onnxYolo::run), clocks.getValue("onnx")),
                verticalOcr = TimedOcr(engine(onnxGraphs, hostDir), clocks.getValue("onnx")),
            ),
            "litert" to TextDetectionRepositoryImpl(
                TimedDetector(
                    CommonYoloTextDetector({ x -> gpuExecutor.submit<FloatArray> { liteYolo.run(x) }.get() }),
                    clocks.getValue("litert"),
                ),
                verticalOcr = TimedOcr(OnDispatcher(engine(liteGraphs, hostDir), gpu), clocks.getValue("litert")),
                maxConcurrentOcr = 1,
            ),
        )
        val warmUp = dataset.pages.first().imageFile.readBytes()
        for (repo in repos.values) repeat(2) { repo.detectAndRecognize(warmUp) }
        onnxGraphs.reset(); liteGraphs.reset()

        val rows = JSONArray()
        for ((index, page) in dataset.pages.withIndex()) {
            val bytes = page.imageFile.readBytes()
            val order = if (index % 2 == 0) repos.keys.toList() else repos.keys.reversed()
            val row = JSONObject().put("page", page.name).put("first", order.first())
            val texts = mutableMapOf<String, List<String>>()
            for (name in order) {
                val clock = clocks.getValue(name).apply { reset() }
                val start = SystemClock.elapsedRealtime()
                val blocks = repos.getValue(name).detectAndRecognize(bytes)
                val total = SystemClock.elapsedRealtime() - start
                val spans = clock.spans.toList()
                val recognizeMs = if (spans.isEmpty()) 0 else spans.maxOf { it[1] } - spans.minOf { it[0] }
                row.put(name, JSONObject()
                    .put("ms", total)
                    .put("detect_ms", clock.detectMs)
                    .put("recognize_ms", recognizeMs)
                    .put("regions", spans.size)
                    .put("blocks", blocks.size))
                texts[name] = blocks.map { it.text }
            }
            row.put("same_blocks", texts["onnx"] == texts["litert"])
            row.put("texts", JSONObject(texts.mapValues { JSONArray(it.value) }))
            rows.put(row)
        }
        val result = JSONObject()
            .put("runtime", runtimeInfo(environment, config).put("decoder_config", decoderConfig.label).put("kv_transfer", liteHayai.kvTransfer.name))
            .put("compile_ms", JSONObject(liteHayai.compileMs).put("yolo", liteYolo.compileMs))
            .put("graphs", JSONObject().put("onnx", onnxGraphs.snapshot()).put("litert", liteGraphs.snapshot()))
            .put("pages", rows)
        onnxGraphs.close()
        onnxYolo.close()
        withContext(gpu) {
            liteGraphs.close()
            liteYolo.close()
            environment.close()
        }
        gpuExecutor.shutdown()
        dataset.writeOutput("litert-paired-${config.label}-${liteHayai.kvTransfer.name.lowercase()}.json", result)
    }

    /** `-e encoderFile <name>` under `hayai-litert-gpu`: the GPU encoder the hybrid loads. */
    private fun hybridEncoderFile(): File = File(File(evalRoot, "hayai-litert-gpu"), args.getString("encoderFile") ?: "hayai_gpu_encoder_i8f16.tflite")
        .also { assumeTrue("Missing ${it.absolutePath}", it.isFile) }

    /** All ground-truth crops through the hybrid (GPU encoder, ORT prefill/decode), in the scorer's shape. */
    @Test
    fun hybridRecognition() {
        val dataset = EvalDataset.loadOrSkip(context)
        val config = LiteRtConfig.fromArguments(args)
        val hostDir = stagedOnnxDir()
        val graphs = HybridHayaiGraphs(hybridEncoderFile(), hostDir, config)
        val engine = engine(graphs, hostDir)
        val results = JSONObject()
        try {
            for (c in crops(dataset)) {
                val start = SystemClock.elapsedRealtime()
                val text = engine.recognizeRgb(c.rgb, c.height, c.width)
                results.put(c.key, JSONObject().put("text", text).put("ms", SystemClock.elapsedRealtime() - start))
            }
        } finally {
            engine.close()
        }
        dataset.writeOutput("recognition.json", results)
        dataset.writeOutput(
            "runtime.json",
            JSONObject()
                .put("litert_maven", LITERT_VERSION)
                .put("config", config.label + " encoder=" + hybridEncoderFile().name)
                .put("decoder_config", "onnxruntime ${OrtEnvironment.getEnvironment().version} CPU (app prefill/decode, NO_ARENA, 4 threads)")
                .put("kv_transfer", "n/a")
                .put("compile_ms", JSONObject().put("encoder", graphs.encoderCompileMs)),
        )
        assertEquals(dataset.itemCount, results.length())
    }

    /**
     * The app's pipeline against the hybrid, paired per page with the order alternated. Both run four
     * recognitions at a time; the hybrid's encoder calls queue for the GPU while prefill and decode
     * run in parallel on ORT. `-e hybridYolo onnx|gpu` picks the hybrid's detector (default: the
     * app's, so only the encoder differs).
     */
    @Test
    fun hybridPaired() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val config = LiteRtConfig.fromArguments(args)
        val hostDir = stagedOnnxDir()
        val onnxGraphs = TimedGraphs(HayaiOnnxGraphs(onnxBaselineDir()).apply { initialize() })
        val hybrid = HybridHayaiGraphs(hybridEncoderFile(), hostDir, config)
        val hybridGraphs = TimedGraphs(hybrid)
        val onnxYolo = OnnxYoloRunner(OnnxYoloRunner.pushedFile(context))
        val appDetector: TextDetector = CommonYoloTextDetector(onnxYolo::run)
        val yoloMode = args.getString("hybridYolo") ?: "onnx"
        val yoloExecutor = Executors.newSingleThreadExecutor()
        val liteYolo = if (yoloMode == "gpu") {
            yoloExecutor.submit<Pair<Environment, LiteRtYoloRunner>> {
                val e = Environment.create()
                e to LiteRtYoloRunner(yoloFile(), e, config)
            }.get()
        } else {
            null
        }
        val hybridDetector: TextDetector = if (liteYolo != null) {
            CommonYoloTextDetector({ x -> yoloExecutor.submit<FloatArray> { liteYolo.second.run(x) }.get() })
        } else {
            appDetector
        }
        val clocks = mapOf("onnx" to StageClock(), "litert" to StageClock())
        val repos = linkedMapOf(
            "onnx" to TextDetectionRepositoryImpl(
                TimedDetector(appDetector, clocks.getValue("onnx")),
                verticalOcr = TimedOcr(engine(onnxGraphs, hostDir), clocks.getValue("onnx")),
            ),
            "litert" to TextDetectionRepositoryImpl(
                TimedDetector(hybridDetector, clocks.getValue("litert")),
                verticalOcr = TimedOcr(engine(hybridGraphs, hostDir), clocks.getValue("litert")),
            ),
        )
        val warmUp = dataset.pages.first().imageFile.readBytes()
        for (repo in repos.values) repeat(2) { repo.detectAndRecognize(warmUp) }
        onnxGraphs.reset(); hybridGraphs.reset()

        val rows = JSONArray()
        // Per configuration, the scorer's pipeline.json: page -> {ms, blocks: [{bbox: [x, y, w, h], text}]}.
        val pipelines = mapOf("onnx" to JSONObject(), "litert" to JSONObject())
        for ((index, page) in dataset.pages.withIndex()) {
            val bytes = page.imageFile.readBytes()
            val order = if (index % 2 == 0) repos.keys.toList() else repos.keys.reversed()
            val row = JSONObject().put("page", page.name).put("first", order.first())
            val texts = mutableMapOf<String, List<String>>()
            for (name in order) {
                val clock = clocks.getValue(name).apply { reset() }
                val start = SystemClock.elapsedRealtime()
                val blocks = repos.getValue(name).detectAndRecognize(bytes)
                val total = SystemClock.elapsedRealtime() - start
                val spans = clock.spans.toList()
                val recognizeMs = if (spans.isEmpty()) 0 else spans.maxOf { it[1] } - spans.minOf { it[0] }
                row.put(name, JSONObject()
                    .put("ms", total)
                    .put("detect_ms", clock.detectMs)
                    .put("recognize_ms", recognizeMs)
                    .put("regions", spans.size)
                    .put("blocks", blocks.size))
                texts[name] = blocks.map { it.text }
                pipelines.getValue(name).put(page.name, JSONObject().put("ms", total).put("blocks", JSONArray().apply {
                    for (b in blocks) put(JSONObject().put("bbox", JSONArray(listOf(b.x, b.y, b.width, b.height))).put("text", b.text))
                }))
            }
            row.put("same_blocks", texts["onnx"] == texts["litert"])
            row.put("texts", JSONObject(texts.mapValues { JSONArray(it.value) }))
            rows.put(row)
        }
        val result = JSONObject()
            .put("runtime", JSONObject()
                .put("mode", "hybrid: encoder ${config.label} (${hybridEncoderFile().name}), prefill/decode onnxruntime CPU")
                .put("hybrid_yolo", yoloMode))
            .put("compile_ms", JSONObject().put("encoder", hybrid.encoderCompileMs).put("yolo", liteYolo?.second?.compileMs ?: 0))
            .put("graphs", JSONObject().put("onnx", onnxGraphs.snapshot()).put("litert", hybridGraphs.snapshot()))
            .put("pages", rows)
        onnxGraphs.close()
        hybridGraphs.close()
        onnxYolo.close()
        if (liteYolo != null) {
            yoloExecutor.submit<Unit> { liteYolo.second.close(); liteYolo.first.close() }.get()
        }
        yoloExecutor.shutdown()
        dataset.writeOutput("hybrid-paired-yolo$yoloMode.json", result)
        dataset.writeOutput("pipeline-app.json", pipelines.getValue("onnx"))
        dataset.writeOutput("pipeline-hybrid-yolo$yoloMode.json", pipelines.getValue("litert"))
    }

    private class OnDispatcher(
        private val delegate: MangaOcrEngine,
        private val dispatcher: CoroutineDispatcher,
    ) : MangaOcrEngine {
        override suspend fun recognizeText(imageBytes: ByteArray): String =
            withContext(dispatcher) { delegate.recognizeText(imageBytes) }

        override suspend fun recognizeText(image: PlatformImage): String =
            withContext(dispatcher) { delegate.recognizeText(image) }

        override fun close() = delegate.close()
    }

    private companion object {
        const val LITERT_VERSION = "2.2.0"
        const val TAG = "LITERT_GPU"
    }
}
