package com.manhwaviewer.data.ocr.eval

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.closingOnFailure
import com.manhwaviewer.data.ocr.inference.YoloRunner
import java.io.File
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
 * The run also writes the blocks each page produced, so the same run yields both the page time
 * and the pipeline accuracy — measured together rather than multiplied together from separate
 * runs.
 */
class HayaiPageTimingRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun modelDir(): File {
        val dir = File(File(context.filesDir, "ocr-eval"), "hayai-onnx")
        assumeTrue("No Hayai ONNX models at ${dir.absolutePath}", dir.isDirectory)
        return dir
    }

    /** All three graphs on ORT from the pushed directory holding the full ONNX set. */
    private fun ortEngine(dir: File): MangaOcrEngine {
        val graphs = HayaiOnnxGraphs(dir)
        return closingOnFailure(graphs::close) {
            graphs.initialize()
            HayaiEngineFactory.engine(graphs, dir, HayaiEngineFactory.positionBase(dir))
        }
    }

    private fun repository(yolo: YoloRunner, engine: MangaOcrEngine) =
        TextDetectionRepositoryImpl(CommonYoloTextDetector(yolo, YoloPostProcessor.CONF_THRESHOLD), verticalOcr = engine)

    @Test
    fun pageTiming() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val dir = modelDir()
        val yolo = YoloRunner(context)
        val hayai = ortEngine(dir)
        val repos = linkedMapOf("hayai" to repository(yolo, hayai))
        val names = repos.keys.toList()

        // 첫 페이지는 모델 로딩이 섞이므로 계측에서 제외한다.
        val warmUp = dataset.pages.first().imageFile.readBytes()
        for (repo in repos.values) repo.detectAndRecognize(warmUp)

        val rows = JSONArray()
        val pipelines = names.associateWith { JSONObject() }
        try {
            for ((index, page) in dataset.pages.withIndex()) {
                val bytes = page.imageFile.readBytes()
                val shift = index % names.size
                val order = names.drop(shift) + names.take(shift)
                val row = JSONObject().put("page", page.name).put("first", order.first())
                for (name in order) {
                    val start = SystemClock.elapsedRealtime()
                    val blocks = repos.getValue(name).detectAndRecognize(bytes)
                    row.put("${name}_ms", SystemClock.elapsedRealtime() - start)
                    row.put("${name}_blocks", blocks.size)
                    val encoded = JSONArray()
                    for (b in blocks) {
                        encoded.put(
                            JSONObject()
                                .put("bbox", JSONArray(listOf(b.x, b.y, b.width, b.height)))
                                .put("text", b.text),
                        )
                    }
                    pipelines.getValue(name).put(
                        page.name,
                        JSONObject().put("ms", row.getLong("${name}_ms")).put("blocks", encoded),
                    )
                }
                rows.put(row)
            }
        } finally {
            hayai.close()
        }
        dataset.writeOutput("page-timing.json", JSONObject().put("pages", rows))
        for ((name, pipeline) in pipelines) dataset.writeOutput("pipeline-$name.json", pipeline)
    }
}
