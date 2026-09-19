package com.manhwaviewer.data.ocr.eval

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.CommonMangaOcrEngine
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.MlKitTextRecognizer
import com.manhwaviewer.data.ocr.PlatformModelPaths
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.hayai.HayaiMangaOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiTokenizer
import com.manhwaviewer.data.ocr.inference.OcrDecoderRunner
import com.manhwaviewer.data.ocr.inference.OcrEncoderRunner
import com.manhwaviewer.data.ocr.inference.YoloRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How much memory a page of OCR costs, for one engine at a time.
 *
 * One engine per invocation (`-e engine hayai|mangaocr`) because the app only ever holds one,
 * and because holding several at once is what got an earlier sweep reaped by lowmemorykiller —
 * a two-engine number would not describe anything the app does.
 *
 * PSS rather than heap: ORT's weights and arenas are native, and the graphs are mmapped from
 * disk, so the JVM heap says almost nothing about the real cost.
 */
class OcrMemoryRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pssKb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss

    private fun snapshot(): JSONObject {
        val mi = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        return JSONObject()
            .put("total_pss_kb", mi.totalPss)
            .put("native_pss_kb", mi.nativePss)
            .put("dalvik_pss_kb", mi.dalvikPss)
            .put("other_pss_kb", mi.otherPss)
            .put("native_heap_alloc_kb", (Debug.getNativeHeapAllocatedSize() / 1024).toInt())
            .put("jvm_used_kb", ((rt.totalMemory() - rt.freeMemory()) / 1024).toInt())
    }

    private fun positionBase(dir: File): FloatArray {
        val bytes = File(dir, "position_base.f32").readBytes()
        val expected = 16 * 16 * 768
        require(bytes.size == expected * 4) { "position_base.f32 holds ${bytes.size / 4} floats" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(expected).also { buffer.get(it) }
    }

    private fun hayai(): MangaOcrEngine {
        val dir = File(File(context.filesDir, "ocr-eval"), "hayai-onnx")
        assumeTrue("No Hayai ONNX models at ${dir.absolutePath}", dir.isDirectory)
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        fun id(t: String) = tokenizer.tokenIdOf(t) ?: error("tokenizer.json carries no $t")
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = HayaiOnnxGraphs(dir).apply { initialize() },
                tokenizer = tokenizer,
                positionBase = positionBase(dir),
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    private fun mangaOcr(): MangaOcrEngine = CommonMangaOcrEngine(
        encoder = OcrEncoderRunner(context),
        decoder = OcrDecoderRunner(context),
        vocabLoader = {
            context.assets.open(PlatformModelPaths.VOCAB_FILE_PATH).bufferedReader().readLines()
        },
    )

    @Test
    fun memory() = runBlocking {
        val which = InstrumentationRegistry.getArguments().getString("engine")
            ?: error("pass -e engine hayai|mangaocr")
        val dataset = EvalDataset.loadOrSkip(context)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val device = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        val baseline = snapshot()
        val engine = if (which == "hayai") hayai() else mangaOcr()
        val repo = TextDetectionRepositoryImpl(
            CommonYoloTextDetector(YoloRunner(context)),
            verticalOcr = engine,
            platformRecognizer = MlKitTextRecognizer(),
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
        try {
            for (page in dataset.pages) repo.detectAndRecognize(page.imageFile.readBytes())
        } finally {
            running.set(false)
            poller.join()
            engine.close()
        }
        val after = snapshot()

        dataset.writeOutput(
            "memory-$which.json",
            JSONObject()
                .put("engine", which)
                .put("device_total_ram_kb", device.totalMem / 1024)
                .put("device_threshold_kb", device.threshold / 1024)
                .put("baseline", baseline)
                .put("loaded", loaded)
                .put("peak_total_pss_kb", peak.get())
                .put("after_close", after)
                .put("pages", dataset.pages.size),
        )
    }
}
