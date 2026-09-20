package com.manhwaviewer.data.ocr.eval

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.BuildConfig
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.DetectedRegion
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.MlKitTextRecognizer
import com.manhwaviewer.data.ocr.PlatformTextRecognizer
import com.manhwaviewer.data.ocr.RecognizedHorizontalRegion
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.TextDetector
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.image.PlatformImage
import com.manhwaviewer.data.ocr.inference.YoloRunner
import com.manhwaviewer.data.translation.GoogleTranslationService
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * Where a page's time goes, stage by stage, through the repository the app actually uses.
 *
 * `HayaiPageTimingRunner` times the whole `detectAndRecognize` call. This one wraps the detector,
 * the platform recognizer and the OCR engine in timing decorators, so the same call also yields
 * detection, MLKit and per-region recognition times without touching production code, and then
 * sends the recognized text through the translation service the app is wired to.
 */
class PageStageTimingRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class StageClock {
        @Volatile var detectMs = 0L
        @Volatile var detectRegions = 0
        @Volatile var mlkitMs = 0L
        val regionSpans: MutableList<LongArray> = Collections.synchronizedList(mutableListOf())

        fun reset() {
            detectMs = 0; detectRegions = 0; mlkitMs = 0; regionSpans.clear()
        }
    }

    private class TimedDetector(private val delegate: TextDetector, private val clock: StageClock) : TextDetector {
        override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
            val start = SystemClock.elapsedRealtime()
            return delegate.detect(imageBytes).also {
                clock.detectMs = SystemClock.elapsedRealtime() - start
                clock.detectRegions = it.size
            }
        }
    }

    private class TimedPlatformRecognizer(
        private val delegate: PlatformTextRecognizer,
        private val clock: StageClock,
    ) : PlatformTextRecognizer {
        override suspend fun recognizeText(imageBytes: ByteArray): String = delegate.recognizeText(imageBytes)

        override suspend fun detectHorizontalText(imageBytes: ByteArray): List<RecognizedHorizontalRegion> {
            val start = SystemClock.elapsedRealtime()
            return delegate.detectHorizontalText(imageBytes).also { clock.mlkitMs = SystemClock.elapsedRealtime() - start }
        }
    }

    private class TimedOcrEngine(private val delegate: MangaOcrEngine, private val clock: StageClock) : MangaOcrEngine {
        override suspend fun recognizeText(imageBytes: ByteArray): String = delegate.recognizeText(imageBytes)

        override suspend fun recognizeText(image: PlatformImage): String {
            val start = SystemClock.elapsedRealtime()
            return delegate.recognizeText(image).also { clock.regionSpans.add(longArrayOf(start, SystemClock.elapsedRealtime())) }
        }

        override fun close() = delegate.close()
    }

    @Test
    fun pageStages() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val yolo = YoloRunner(context)
        val clock = StageClock()
        val engines = linkedMapOf("hayai" to HayaiEngineFactory.fromAssets(context))
        val repos = engines.mapValues { (_, engine) ->
            TextDetectionRepositoryImpl(
                TimedDetector(CommonYoloTextDetector(yolo), clock),
                verticalOcr = TimedOcrEngine(engine, clock),
                platformRecognizer = TimedPlatformRecognizer(MlKitTextRecognizer(), clock),
            )
        }
        val names = repos.keys.toList()
        val translator = BuildConfig.GOOGLE_TRANSLATE_API_KEY.takeIf { it.isNotBlank() }?.let { GoogleTranslationService(it) }

        // 첫 페이지는 모델 로딩과 TLS 연결 수립이 섞이므로 계측에서 제외한다.
        val warmUp = dataset.pages.first().imageFile.readBytes()
        for (repo in repos.values) repo.detectAndRecognize(warmUp)
        translator?.translate(listOf("テスト"), "ja", "ko")

        val rows = JSONArray()
        try {
            for ((index, page) in dataset.pages.withIndex()) {
                val readStart = SystemClock.elapsedRealtime()
                val bytes = page.imageFile.readBytes()
                val row = JSONObject()
                    .put("page", page.name)
                    .put("read_ms", SystemClock.elapsedRealtime() - readStart)
                    .put("image_bytes", bytes.size)

                val shift = index % names.size
                var texts = emptyList<String>()
                for (name in names.drop(shift) + names.take(shift)) {
                    clock.reset()
                    val start = SystemClock.elapsedRealtime()
                    val blocks = repos.getValue(name).detectAndRecognize(bytes)
                    val totalMs = SystemClock.elapsedRealtime() - start
                    val spans = clock.regionSpans.toList()
                    row.put(
                        name,
                        JSONObject()
                            .put("total_ms", totalMs)
                            .put("detect_ms", clock.detectMs)
                            .put("detect_regions", clock.detectRegions)
                            .put("mlkit_ms", clock.mlkitMs)
                            .put("recognized_regions", spans.size)
                            .put("recognition_wall_ms", if (spans.isEmpty()) 0 else spans.maxOf { it[1] } - spans.minOf { it[0] })
                            .put("recognition_sum_ms", spans.sumOf { it[1] - it[0] })
                            .put("region_ms", JSONArray(spans.map { it[1] - it[0] }))
                            .put("blocks", blocks.size),
                    )
                    texts = blocks.map { it.text }
                }

                if (translator != null && texts.isNotEmpty()) {
                    val start = SystemClock.elapsedRealtime()
                    val translated = translator.translate(texts, "ja", "ko")
                    row.put(
                        "translate",
                        JSONObject()
                            .put("engine", translator.engineName)
                            .put("ms", SystemClock.elapsedRealtime() - start)
                            .put("items", translated.size)
                            .put("chars", texts.sumOf { it.length }),
                    )
                }
                rows.put(row)
            }
        } finally {
            engines.values.forEach { it.close() }
        }
        dataset.writeOutput("page-stages.json", JSONObject().put("pages", rows))
    }
}
