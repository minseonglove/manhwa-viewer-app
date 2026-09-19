package com.manhwaviewer.data.ocr.eval

import ai.onnxruntime.OrtEnvironment
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.DetectedBox
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.hayai.HayaiMangaOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiTokenizer
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.readRgbHwc
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hayai OCR through the Kotlin host port and ONNX Runtime, on the device.
 *
 * Writes `recognition.json` in the same shape the app's runner does, so the same scorer reads
 * it and the result can be diffed item by item against the reference implementation's run.
 * That diff is the point: the host arithmetic was ported by hand, and only running the whole
 * chain against the same graphs shows whether the port reproduces the reference.
 *
 * Models are pushed to `filesDir/ocr-eval/hayai-onnx` rather than bundled: at ~148 MB they do
 * not belong in an APK, and the shared-weight blob has to sit beside the graphs that reference it.
 */
class HayaiOnnxEvalRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun modelDir(): File {
        val dir = File(File(context.filesDir, "ocr-eval"), "hayai-onnx")
        assumeTrue("No Hayai ONNX models at ${dir.absolutePath}", dir.isDirectory)
        for (name in listOf(
            "hayai_encoder.onnx", "hayai_prefill.onnx", "hayai_decode.onnx",
            "position_base.f32", "tokenizer.json",
        )) {
            assumeTrue("Missing $name in ${dir.absolutePath}", File(dir, name).isFile)
        }
        return dir
    }

    private fun positionBase(dir: File): FloatArray {
        val bytes = File(dir, "position_base.f32").readBytes()
        val expected = 16 * 16 * 768
        require(bytes.size == expected * 4) {
            "position_base.f32 holds ${bytes.size / 4} floats, expected $expected"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(expected).also { buffer.get(it) }
    }

    @Test
    fun recognition() {
        val dataset = EvalDataset.loadOrSkip(context)
        val dir = modelDir()
        // `-e xnnpack <threads>` 로 실행 공급자를 바꿔 같은 기기에서 A/B 한다.
        val xnnpackThreads = InstrumentationRegistry.getArguments()
            .getString("xnnpack")?.toIntOrNull() ?: 0
        val graphs = HayaiOnnxGraphs(dir).apply { initialize(xnnpackThreads) }
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        // Through the adapter, because the grid choice and the resize it performs are part of
        // what this run measures.
        val engine = HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = graphs,
                tokenizer = tokenizer,
                positionBase = positionBase(dir),
                bosId = requireToken(tokenizer, "<bos>"),
                eosId = requireToken(tokenizer, "<eos>"),
                padId = requireToken(tokenizer, "<pad>"),
            )
        )
        val results = JSONObject()
        try {
            for (page in dataset.pages) {
                val image = decodeImage(page.imageFile.readBytes())
                for (item in page.items) {
                    // The same padding rule the app applies to a detection, so the crop matches
                    // the reference's.
                    val padded = YoloPostProcessor.addPadding(
                        DetectedBox(item.x, item.y, item.width, item.height,
                            confidence = 1f, isJapanese = true),
                        image.width,
                        image.height,
                    )
                    val crop = cropToPlatformImage(image, padded.x, padded.y, padded.width, padded.height)
                    val rgb = readRgbHwc(crop)
                    val start = SystemClock.elapsedRealtime()
                    val text = engine.recognizeRgb(rgb, crop.height, crop.width)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    results.put(
                        "${page.name}/${item.id}",
                        JSONObject().put("text", text).put("ms", elapsed),
                    )
                }
            }
        } finally {
            engine.close()
        }
        dataset.writeOutput("recognition.json", results)
        // 런타임이 스스로 보고하는 버전과 공급자. 빌드 설정이 아니라 실제로 실행한 것을 남긴다 --
        // 이 값이 없으면 같은 모델·같은 입력의 두 실행이 왜 다른지 사후에 귀속시킬 수 없다.
        dataset.writeOutput(
            "runtime.json",
            JSONObject()
                .put("onnxruntime", OrtEnvironment.getEnvironment().version)
                .put(
                    "available_providers",
                    OrtEnvironment.getAvailableProviders().joinToString(",") { it.name },
                )
                .put("providers_used", if (xnnpackThreads > 0) "XnnpackExecutionProvider($xnnpackThreads),CPUExecutionProvider" else "CPUExecutionProvider"),
        )
        assertEquals(dataset.itemCount, results.length())
    }

    /** Looked up rather than hard-coded, as the reference does. */
    private fun requireToken(tokenizer: HayaiTokenizer, token: String): Int =
        tokenizer.tokenIdOf(token)
            ?: error("tokenizer.json carries no $token; the loop cannot start or stop without it")
}
