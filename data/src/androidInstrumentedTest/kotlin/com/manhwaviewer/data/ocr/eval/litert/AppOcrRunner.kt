package com.manhwaviewer.data.ocr.eval.litert

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.DetectedRegion
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.TextDetector
import com.manhwaviewer.data.ocr.eval.EvalDataset
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.hayai.HayaiLiteRtEncoder
import com.manhwaviewer.data.ocr.hayai.HayaiModelStager
import com.manhwaviewer.data.ocr.image.PlatformImage
import com.manhwaviewer.data.ocr.inference.LiteRtThread
import com.manhwaviewer.data.ocr.inference.YoloRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * The OCR pipeline exactly as `AppModule` assembles it — `CommonYoloTextDetector(YoloRunner(context))`
 * and `HayaiEngineFactory.fromAssets(context)` — rather than the measurement-only pieces.
 *
 * `-e preferGpu false` forces the CPU fallback both models take on a device whose GPU fails or
 * fails its self-check.
 */
class AppOcrRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val preferGpu = args.getString("preferGpu")?.toBooleanStrict() ?: true

    /**
     * Opens both LiteRT models the way the app does and records how long each took and where it
     * landed. `-e clearGpuCache true` deletes the GPU program cache first, for the first-launch cost.
     */
    @Test
    fun appColdStart() {
        val dataset = EvalDataset.loadOrSkip(context)
        val cache = File(context.cacheDir, "litert-gpu")
        val cleared = args.getString("clearGpuCache")?.toBooleanStrict() ?: false
        if (cleared) cache.deleteRecursively()
        val cacheBefore = cache.walkTopDown().filter { it.isFile }.sumOf { it.length() }

        var start = SystemClock.elapsedRealtime()
        val dir = HayaiModelStager.stage(context.assets::open, File(context.cacheDir, HayaiModelStager.ASSET_DIR), HayaiEngineFactory.MODEL_VERSION)
        val stageMs = SystemClock.elapsedRealtime() - start
        val bytes = File(dir, "position_base.f32").readBytes()
        val positionBase = FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }

        start = SystemClock.elapsedRealtime()
        val encoder = HayaiLiteRtEncoder(context, positionBase, preferGpu)
        val encoderMs = SystemClock.elapsedRealtime() - start

        start = SystemClock.elapsedRealtime()
        val yolo = YoloRunner(context, preferGpu).apply { initialize() }
        val yoloMs = SystemClock.elapsedRealtime() - start

        val report = JSONObject()
            .put("prefer_gpu", preferGpu)
            .put("gpu_cache_cleared", cleared)
            .put("gpu_cache_bytes_before", cacheBefore)
            .put("gpu_cache_bytes_after", cache.walkTopDown().filter { it.isFile }.sumOf { it.length() })
            .put("stage_ms", stageMs)
            .put("encoder", JSONObject().put("open_ms", encoderMs).put("accelerator", encoder.accelerator.name))
            .put("yolo", JSONObject().put("open_ms", yoloMs).put("accelerator", yolo.accelerator?.name))
        encoder.close()
        yolo.close()
        dataset.writeOutput("app-cold-start-${if (preferGpu) "gpu" else "cpu"}-${if (cleared) "cleared" else "cached"}.json", report)
    }

    /**
     * How far the GPU's YOLO output normally sits from the CPU's, on real pages: the largest gap
     * among the ten strongest scores, and whether the confident (≥ 0.3) boxes match. This is what the
     * GPU self-check's limits are set from.
     */
    @Test
    fun yoloGpuCpuGap() {
        val dataset = EvalDataset.loadOrSkip(context)
        val cpu = YoloRunner(context, preferGpu = false).apply { initialize() }
        val gpuModel = LiteRtThread.call {
            com.google.ai.edge.litert.CompiledModel.create(
                context.assets, com.manhwaviewer.data.ocr.PlatformModelPaths.YOLO_MODEL_PATH,
                LiteRtConfig().options(), LiteRtThread.environment(),
            )
        }
        fun gpuRun(x: FloatArray): FloatArray = LiteRtThread.call {
            val input = gpuModel.createInputBuffer("args_0", "serving_default")
            val output = gpuModel.createOutputBuffer("output_0", "serving_default")
            try {
                input.writeFloat(x)
                gpuModel.run(mapOf("args_0" to input), mapOf("output_0" to output), "serving_default")
                output.readFloat()
            } finally {
                input.close(); output.close()
            }
        }
        fun top(raw: FloatArray) = (0 until 300).map { raw[it * 6 + 4] }.sortedDescending().take(10)
        fun boxes(raw: FloatArray) = com.manhwaviewer.data.ocr.YoloPostProcessor.nonMaxSuppression(
            com.manhwaviewer.data.ocr.YoloPostProcessor.parseAndFilter(raw, 1f, 0, 0, 640, 640, confThreshold = 0.3f),
        )
        val rows = JSONArray()
        for (page in dataset.pages) {
            val image = com.manhwaviewer.data.ocr.image.decodeImage(page.imageFile.readBytes())
            val x = com.manhwaviewer.data.ocr.image.resizeAndPadToFloatArray(image, 640, com.manhwaviewer.data.ocr.image.NormRange.ZERO_ONE, 0.5f)
            val g = gpuRun(x)
            val c = cpu.run(x)
            val gap = top(g).zip(top(c)).maxOf { (a, b) -> kotlin.math.abs(a - b) }
            rows.put(JSONObject().put("page", page.name).put("top_gap", gap.toDouble())
                .put("boxes_gpu", boxes(g).size).put("boxes_cpu", boxes(c).size)
                .put("boxes_agree", com.manhwaviewer.data.ocr.inference.GpuSelfCheck.boxesAgree(boxes(g), boxes(c))))
        }
        LiteRtThread.call { gpuModel.close() }
        cpu.close()
        dataset.writeOutput("yolo-gpu-cpu-gap.json", JSONObject().put("pages", rows))
    }

    private class Clock {
        @Volatile var detectMs = 0L
        val spans: MutableList<LongArray> = Collections.synchronizedList(mutableListOf())
    }

    private class TimedDetector(private val delegate: TextDetector, private val clock: Clock) : TextDetector {
        override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
            val start = SystemClock.elapsedRealtime()
            return delegate.detect(imageBytes).also { clock.detectMs = SystemClock.elapsedRealtime() - start }
        }
    }

    private class TimedOcr(private val delegate: MangaOcrEngine, private val clock: Clock) : MangaOcrEngine {
        override suspend fun recognizeText(imageBytes: ByteArray): String = delegate.recognizeText(imageBytes)

        override suspend fun recognizeText(image: PlatformImage): String {
            val start = SystemClock.elapsedRealtime()
            return delegate.recognizeText(image).also { clock.spans.add(longArrayOf(start, SystemClock.elapsedRealtime())) }
        }

        override fun close() = delegate.close()
    }

    /** All pages through the app's repository; writes the scorer's `pipeline.json` shape plus stage times. */
    @Test
    fun appPipeline() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val clock = Clock()
        val yolo = YoloRunner(context, preferGpu)
        val engine = HayaiEngineFactory.fromAssets(context, preferGpu)
        val repo = TextDetectionRepositoryImpl(
            TimedDetector(CommonYoloTextDetector(yolo), clock),
            verticalOcr = TimedOcr(engine, clock),
        )
        val warmUp = dataset.pages.first().imageFile.readBytes()
        repeat(2) { repo.detectAndRecognize(warmUp) }

        val pipeline = JSONObject()
        val stages = JSONArray()
        for (page in dataset.pages) {
            clock.spans.clear()
            val start = SystemClock.elapsedRealtime()
            val blocks = repo.detectAndRecognize(page.imageFile.readBytes())
            val total = SystemClock.elapsedRealtime() - start
            val spans = clock.spans.toList()
            pipeline.put(page.name, JSONObject().put("ms", total).put("blocks", JSONArray().apply {
                for (b in blocks) put(JSONObject().put("bbox", JSONArray(listOf(b.x, b.y, b.width, b.height))).put("text", b.text))
            }))
            stages.put(JSONObject()
                .put("page", page.name)
                .put("ms", total)
                .put("detect_ms", clock.detectMs)
                .put("recognize_ms", if (spans.isEmpty()) 0 else spans.maxOf { it[1] } - spans.minOf { it[0] })
                .put("regions", spans.size))
        }
        engine.close()
        yolo.close()
        val label = if (preferGpu) "gpu" else "cpu"
        dataset.writeOutput("app-pipeline-$label.json", pipeline)
        dataset.writeOutput("app-stages-$label.json", JSONObject().put("yolo_accelerator", yolo.accelerator?.name).put("pages", stages))
    }
}
