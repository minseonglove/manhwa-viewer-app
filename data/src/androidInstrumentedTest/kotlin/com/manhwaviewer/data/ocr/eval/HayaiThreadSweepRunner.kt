package com.manhwaviewer.data.ocr.eval

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.MlKitTextRecognizer
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.hayai.HayaiMangaOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiTokenizer
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
 * Does capping each session's intra-op threads let the four-way region concurrency pay off?
 *
 * The page timing showed `MAX_CONCURRENT_OCR = 4` buying almost nothing: one inference at ORT's
 * default thread count already takes every core, so four at once share the same cores plus
 * synchronisation. Capping the threads per inference is the obvious counter-move, and only the
 * device can say whether it wins.
 *
 * Two engines at a time — ORT's default and one candidate, passed as `-e intraop N` — because
 * holding five sessions at once got the process reaped by lowmemorykiller (each carries about
 * 152 MB of graphs). They alternate per page, so the pair is compared under the same thermal
 * conditions rather than across runs.
 */
class HayaiThreadSweepRunner {

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

    private fun engine(dir: File, intraOp: Int): MangaOcrEngine {
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        fun id(token: String) = tokenizer.tokenIdOf(token) ?: error("tokenizer.json carries no $token")
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = HayaiOnnxGraphs(dir).apply { initialize(intraOpThreads = intraOp) },
                tokenizer = tokenizer,
                positionBase = positionBase(dir),
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    @Test
    fun threadSweep() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val dir = modelDir()
        val candidate = InstrumentationRegistry.getArguments().getString("intraop")?.toIntOrNull()
            ?: error("pass -e intraop <threads>; the candidate is compared against ORT's default")
        val settings = listOf(0, candidate)
        val engines = settings.associateWith { engine(dir, it) }
        val repos = engines.mapValues {
            TextDetectionRepositoryImpl(
                CommonYoloTextDetector(YoloRunner(context)),
                verticalOcr = it.value,
                platformRecognizer = MlKitTextRecognizer(),
            )
        }
        val warmUp = dataset.pages.first().imageFile.readBytes()
        for (repo in repos.values) repo.detectAndRecognize(warmUp)

        val rows = JSONArray()
        try {
            for ((index, page) in dataset.pages.withIndex()) {
                val bytes = page.imageFile.readBytes()
                val order = if (index % 2 == 0) settings else settings.reversed()
                val row = JSONObject().put("page", page.name).put("first", order.first())
                for (threads in order) {
                    val start = SystemClock.elapsedRealtime()
                    val blocks = repos.getValue(threads).detectAndRecognize(bytes)
                    row.put("t$threads", SystemClock.elapsedRealtime() - start)
                    row.put("blocks", blocks.size)
                }
                rows.put(row)
            }
        } finally {
            for (e in engines.values) e.close()
        }
        dataset.writeOutput("thread-sweep.json", JSONObject().put("pages", rows))
    }
}
