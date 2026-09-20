package com.manhwaviewer.data.ocr.eval

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.MangaOcrConfig
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.MlKitTextRecognizer
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.hayai.HayaiMangaOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiTokenizer
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.inference.OrtMemoryOptions
import com.manhwaviewer.data.ocr.inference.YoloRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How much memory a page of OCR costs, for one engine at a time.
 *
 * One engine per process, because the app only ever holds one and holding several at once is
 * what got an earlier sweep reaped by lowmemorykiller.
 *
 * PSS rather than heap: ORT's weights and arenas are native, and the graphs are mmapped from
 * disk, so the JVM heap says almost nothing about the real cost.
 *
 * `-e variant a,b,c` picks ONNX Runtime memory settings for Hayai (see [variant]) and `-e yolo`
 * does the same for the detector; each variant runs in its own process so one arena's leftovers
 * never land in another's number. The same run also records how long the pages took, so memory is
 * never judged without its price in time. `default` here means ORT's own defaults, not the app's:
 * what the app ships is `-e variant noarena -e yolo noarena` ([OrtMemoryOptions.NO_ARENA]).
 */
class OcrMemoryRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pssKb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss

    private fun snapshot(): JSONObject {
        val mi = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        val json = JSONObject()
            .put("total_pss_kb", mi.totalPss)
            .put("native_pss_kb", mi.nativePss)
            .put("dalvik_pss_kb", mi.dalvikPss)
            .put("other_pss_kb", mi.otherPss)
            .put("native_heap_alloc_kb", (Debug.getNativeHeapAllocatedSize() / 1024).toInt())
            .put("native_heap_size_kb", (Debug.getNativeHeapSize() / 1024).toInt())
            .put("jvm_used_kb", ((rt.totalMemory() - rt.freeMemory()) / 1024).toInt())
        // 매핑 종류별 PSS. "other" 가 파일 매핑(mmap 된 모델 blob)인지 코드인지 가른다.
        for ((key, value) in mi.memoryStats) json.put(key.removePrefix("summary."), value.toIntOrNull() ?: 0)
        return json
    }

    private fun modelDir(): File {
        val dir = File(File(context.filesDir, "ocr-eval"), "hayai-onnx")
        assumeTrue("No Hayai ONNX models at ${dir.absolutePath}", dir.isDirectory)
        return dir
    }

    private fun positionBase(dir: File): FloatArray {
        val bytes = File(dir, "position_base.f32").readBytes()
        val expected = 16 * 16 * 768
        require(bytes.size == expected * 4) { "position_base.f32 holds ${bytes.size / 4} floats" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(expected).also { buffer.get(it) }
    }

    /** Hayai 세 그래프에 줄 ORT 메모리 옵션. decode 가 [base] 를 받는다. */
    private class HayaiMemorySpec(
        val base: OrtMemoryOptions,
        val encoder: OrtMemoryOptions,
        val prefill: OrtMemoryOptions,
    )

    private fun hayai(spec: HayaiMemorySpec = hayaiSpec("default")): MangaOcrEngine {
        val dir = modelDir()
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        fun id(t: String) = tokenizer.tokenIdOf(t) ?: error("tokenizer.json carries no $t")
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = HayaiOnnxGraphs(dir).apply {
                    initialize(memory = spec.base, encoderMemory = spec.encoder, prefillMemory = spec.prefill)
                },
                tokenizer = tokenizer,
                positionBase = positionBase(dir),
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    /**
     * `-e variant` 의 쉼표 구분 토큰을 옵션으로 옮긴다. 토큰 하나가 손잡이 하나를 ORT 기본값의
     * 반대로 돌리므로, 조합을 재컴파일 없이 시험할 수 있다.
     */
    private fun variant(spec: String): OrtMemoryOptions {
        var options = OrtMemoryOptions()
        for (token in spec.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "default" }) {
            options = when (token) {
                "noarena" -> options.copy(cpuArena = false)
                "nopattern" -> options.copy(memoryPattern = false)
                "noprepack" -> options.copy(prepacking = false)
                "initdev" -> options.copy(initializersOutsideArena = true)
                "shrink" -> options.copy(shrinkArenaAfterRun = true)
                "envthreads" -> options.copy(sharedThreadPool = true)
                "info" -> options.copy(environmentLogLevel = OrtLoggingLevel.ORT_LOGGING_LEVEL_INFO)
                else -> error("unknown variant token '$token'")
            }
        }
        return options
    }

    /**
     * `-e variant` 토큰을 Hayai 세 그래프로 나눈다. `token:encoder` / `token:prefill` / `token:decode` 는
     * 그 그래프에만, 접미사 없는 토큰은 셋 모두에 적용된다.
     */
    private fun hayaiSpec(spec: String): HayaiMemorySpec {
        val all = mutableListOf<String>()
        val per = listOf("encoder", "prefill", "decode").associateWith { mutableListOf<String>() }
        for (token in spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val parts = token.split(':', limit = 2)
            if (parts.size == 1) all += parts[0]
            else (per[parts[1]] ?: error("unknown graph '${parts[1]}' in '$token'")) += parts[0]
        }
        fun of(extra: List<String>) = variant((all + extra).joinToString(","))
        return HayaiMemorySpec(
            base = of(per.getValue("decode")),
            encoder = of(per.getValue("encoder")),
            prefill = of(per.getValue("prefill")),
        )
    }

    @Test
    fun memory() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val spec = args.getString("variant") ?: "default"
        val yoloSpec = args.getString("yolo") ?: "default"
        val concurrency = args.getString("concurrency")?.toInt() ?: DEFAULT_CONCURRENCY
        val dataset = EvalDataset.loadOrSkip(context)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val device = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        val baseline = snapshot()
        val engine = hayai(hayaiSpec(spec))
        // Hayai 는 여기서 이미 그래프를 열었다. 실행 전, 가중치만 올라간 값.
        val opened = snapshot()
        val repo = TextDetectionRepositoryImpl(
            CommonYoloTextDetector(YoloRunner(context, variant(yoloSpec))),
            verticalOcr = engine,
            platformRecognizer = MlKitTextRecognizer(),
            maxConcurrentOcr = concurrency,
        )
        // 첫 페이지에서 모델이 로드된다. 그 뒤가 '켜져 있는 상태' 의 바닥값.
        repo.detectAndRecognize(dataset.pages.first().imageFile.readBytes())
        val loaded = snapshot()

        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val peak = java.util.concurrent.atomic.AtomicInteger(0)
        val poller = thread {
            while (running.get()) {
                peak.accumulateAndGet(pssKb()) { a, b -> maxOf(a, b) }
                Thread.sleep(200)
            }
        }
        val pageMs = JSONArray()
        val start = SystemClock.elapsedRealtime()
        try {
            for (page in dataset.pages) {
                val pageStart = SystemClock.elapsedRealtime()
                repo.detectAndRecognize(page.imageFile.readBytes())
                pageMs.put(SystemClock.elapsedRealtime() - pageStart)
            }
        } finally {
            running.set(false)
            poller.join()
        }
        val totalMs = SystemClock.elapsedRealtime() - start
        val beforeClose = snapshot()
        engine.close()
        val after = snapshot()

        val suffix = buildString {
            if (spec != "default") append("-").append(spec.replace(',', '+'))
            if (yoloSpec != "default") append("-yolo=").append(yoloSpec.replace(',', '+'))
            if (concurrency != DEFAULT_CONCURRENCY) append("-c").append(concurrency)
        }
        dataset.writeOutput(
            "memory-hayai$suffix.json",
            JSONObject()
                .put("engine", "hayai")
                .put("variant", spec)
                .put("yolo", yoloSpec)
                .put("concurrency", concurrency)
                .put("device_total_ram_kb", device.totalMem / 1024)
                .put("device_threshold_kb", device.threshold / 1024)
                .put("baseline", baseline)
                .put("opened", opened)
                .put("loaded", loaded)
                .put("peak_total_pss_kb", peak.get())
                .put("before_close", beforeClose)
                .put("after_close", after)
                .put("pages", dataset.pages.size)
                .put("total_ms", totalMs)
                .put("page_ms", pageMs),
        )
    }

    /**
     * Where the "loaded" number actually lives: each ONNX session on its own, then the whole
     * Hayai engine, then the detector, then MLKit — each opened, exercised and (where it can be)
     * closed before the next, with a snapshot at every edge.
     *
     * The ORT environment is created first and at INFO so the arena's own growth log
     * ("Extended allocation by N bytes") lands in logcat alongside these snapshots.
     */
    @Test
    fun breakdown() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val dir = modelDir()
        val env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_INFO)
        val steps = JSONArray()
        fun mark(name: String) {
            val s = snapshot().put("step", name)
            steps.put(s)
            Log.i(TAG, "$name total_pss=${s.getInt("total_pss_kb")}kB native_heap_alloc=${s.getInt("native_heap_alloc_kb")}kB")
        }
        mark("baseline")

        // 1) 세션 하나씩, 로드만. 실행 없이 가중치와 prepack, 아레나 초기 확장만 든다.
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(4)
        }
        for (name in listOf("hayai_encoder.onnx", "hayai_prefill.onnx", "hayai_decode.onnx")) {
            Log.i(TAG, "opening $name")
            val session = env.createSession(File(dir, name).absolutePath, options)
            mark("session:$name:open")
            session.close()
            mark("session:$name:closed")
        }
        // prefill 과 decode 는 blob 을 공유한다. 둘을 같이 열면 공유가 실제로 절약하는지 드러난다.
        run {
            val prefill = env.createSession(File(dir, "hayai_prefill.onnx").absolutePath, options)
            val decode = env.createSession(File(dir, "hayai_decode.onnx").absolutePath, options)
            mark("session:prefill+decode:open")
            prefill.close()
            decode.close()
            mark("session:prefill+decode:closed")
        }

        // 2) Hayai 엔진 전체: 로드, 인식 1회, 인식 5회, 닫기.
        val engine = hayai()
        mark("hayai:opened")
        val page = dataset.pages.first()
        val image = decodeImage(page.imageFile.readBytes())
        for ((index, item) in page.items.take(5).withIndex()) {
            val crop = cropToPlatformImage(image, item.x, item.y, item.width, item.height)
            engine.recognizeText(crop)
            mark("hayai:recognized:${index + 1}")
        }
        engine.close()
        mark("hayai:closed")

        // 3) YOLO 검출기: 로드, 실행 2회, 닫기.
        val yolo = YoloRunner(context)
        yolo.initialize()
        mark("yolo:opened")
        val blank = FloatArray(3 * MangaOcrConfig.YOLO_INPUT_SIZE * MangaOcrConfig.YOLO_INPUT_SIZE)
        yolo.run(blank)
        mark("yolo:run:1")
        yolo.run(blank)
        mark("yolo:run:2")
        yolo.close()
        mark("yolo:closed")

        // 4) MLKit 일본어 인식기: 닫는 API 가 없어 켜 둔 채로 끝낸다.
        val mlkit = MlKitTextRecognizer()
        mark("mlkit:created")
        mlkit.detectHorizontalText(page.imageFile.readBytes())
        mark("mlkit:run:1")
        mlkit.detectHorizontalText(dataset.pages[1].imageFile.readBytes())
        mark("mlkit:run:2")

        dataset.writeOutput("memory-breakdown.json", JSONObject().put("steps", steps))
    }

    /**
     * Page time of one configuration against another, paired: both open at once, alternating which
     * goes first per page, so the comparison is made under the same thermal state rather than
     * across runs (which drift by several percent on their own).
     */
    private suspend fun paired(
        dataset: EvalDataset,
        a: Pair<String, TextDetectionRepositoryImpl>,
        b: Pair<String, TextDetectionRepositoryImpl>,
    ): JSONObject {
        val pair = listOf(a, b)
        val warmUp = dataset.pages.first().imageFile.readBytes()
        for ((_, repo) in pair) repo.detectAndRecognize(warmUp)
        val rows = JSONArray()
        for ((index, page) in dataset.pages.withIndex()) {
            val bytes = page.imageFile.readBytes()
            val order = if (index % 2 == 0) pair else pair.reversed()
            val row = JSONObject().put("page", page.name).put("first", order.first().first)
            for ((name, repo) in order) {
                val key = if (name == a.first) "a" else "b"
                val start = SystemClock.elapsedRealtime()
                val blocks = repo.detectAndRecognize(bytes)
                row.put("${key}_ms", SystemClock.elapsedRealtime() - start)
                row.put("${key}_blocks", blocks.size)
            }
            rows.put(row)
        }
        return JSONObject().put("a", a.first).put("b", b.first).put("pages", rows)
    }

    /** `-e variant` 를 `-e base`(기본 `default` = ORT 기본값) 와 짝지어 잰다. YOLO 는 공유. */
    @Test
    fun pairedTiming() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val args = InstrumentationRegistry.getArguments()
        val spec = args.getString("variant")
            ?: error("pass -e variant <tokens>; it is compared against -e base (default: ORT defaults)")
        val base = args.getString("base") ?: "default"
        val engines = linkedMapOf(base to hayai(hayaiSpec(base)), spec to hayai(hayaiSpec(spec)))
        val yolo = YoloRunner(context, variant(args.getString("yolo") ?: "default"))
        val repos = engines.map { (name, engine) ->
            name to TextDetectionRepositoryImpl(
                CommonYoloTextDetector(yolo),
                verticalOcr = engine,
                platformRecognizer = MlKitTextRecognizer(),
            )
        }
        val result = try {
            paired(dataset, repos[0], repos[1])
        } finally {
            for (e in engines.values) e.close()
        }
        val suffix = if (base == "default") "" else "-vs-${base.replace(',', '+')}"
        dataset.writeOutput("paired-timing-${spec.replace(',', '+')}$suffix.json", result)
    }

    /**
     * 동시 인식 수의 짝 비교. 엔진과 YOLO 는 하나를 두 저장소가 공유하고 세마포어 크기만 다르다
     * (`-e concurrency N` 대 앱의 4). 엔진 옵션은 `-e variant` (기본 `noarena`, 앱 기본값).
     */
    @Test
    fun pairedConcurrency() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val args = InstrumentationRegistry.getArguments()
        val candidate = args.getString("concurrency")?.toInt()
            ?: error("pass -e concurrency <n>; it is compared against $DEFAULT_CONCURRENCY")
        val spec = args.getString("variant") ?: "noarena"
        val engine = hayai(hayaiSpec(spec))
        val yolo = YoloRunner(context, variant(args.getString("yolo") ?: "noarena"))
        fun repo(n: Int) = "c$n" to TextDetectionRepositoryImpl(
            CommonYoloTextDetector(yolo),
            verticalOcr = engine,
            platformRecognizer = MlKitTextRecognizer(),
            maxConcurrentOcr = n,
        )
        val result = try {
            paired(dataset, repo(DEFAULT_CONCURRENCY), repo(candidate))
        } finally {
            engine.close()
        }
        dataset.writeOutput("paired-concurrency-$candidate.json", result.put("variant", spec))
    }

    private companion object {
        const val TAG = "OCR_MEM"
        /** `TextDetectionRepositoryImpl` 이 앱에서 쓰는 동시 인식 수. */
        const val DEFAULT_CONCURRENCY = 4
    }
}
