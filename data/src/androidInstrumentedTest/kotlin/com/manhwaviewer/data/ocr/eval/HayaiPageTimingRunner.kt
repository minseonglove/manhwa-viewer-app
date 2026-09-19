package com.manhwaviewer.data.ocr.eval

import android.os.SystemClock
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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Wall-clock time per page through the repository the app actually uses.
 *
 * The recognition runner measures one crop at a time, which answers "how fast is the model"
 * and not "how long does a page take" — the app runs four regions at once behind
 * `TextDetectionRepositoryImpl`'s semaphore, and only on the regions YOLO found, not on all
 * 499 ground-truth items. So this drives the real repository and times the whole call.
 *
 * Both engines are measured on the same device over the same pages, alternating which one goes
 * first per page so warm-up and thermal drift do not land on one of them.
 */
class HayaiPageTimingRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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

    private fun hayai(dir: File): MangaOcrEngine {
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        fun id(token: String) = tokenizer.tokenIdOf(token) ?: error("tokenizer.json carries no $token")
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

    private fun repository(engine: MangaOcrEngine) = TextDetectionRepositoryImpl(
        CommonYoloTextDetector(YoloRunner(context)),
        verticalOcr = engine,
        platformRecognizer = MlKitTextRecognizer(),
    )

    @Test
    fun pageTiming() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val dir = modelDir()
        val engines = linkedMapOf("hayai" to hayai(dir), "mangaocr" to mangaOcr())
        val repos = engines.mapValues { repository(it.value) }

        // 첫 페이지는 두 엔진 모두 모델 로딩이 섞이므로 계측에서 제외한다.
        val warmUp = dataset.pages.first()
        for (repo in repos.values) repo.detectAndRecognize(warmUp.imageFile.readBytes())

        val rows = JSONArray()
        try {
            for ((index, page) in dataset.pages.withIndex()) {
                val bytes = page.imageFile.readBytes()
                val order = if (index % 2 == 0) engines.keys.toList() else engines.keys.reversed()
                val row = JSONObject().put("page", page.name)
                for (name in order) {
                    val start = SystemClock.elapsedRealtime()
                    val blocks = repos.getValue(name).detectAndRecognize(bytes)
                    row.put("${name}_ms", SystemClock.elapsedRealtime() - start)
                    row.put("${name}_blocks", blocks.size)
                }
                row.put("first", order.first())
                rows.put(row)
            }
        } finally {
            for (engine in engines.values) engine.close()
        }
        dataset.writeOutput("page-timing.json", JSONObject().put("pages", rows))
    }
}
