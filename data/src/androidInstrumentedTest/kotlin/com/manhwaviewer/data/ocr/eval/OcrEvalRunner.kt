package com.manhwaviewer.data.ocr.eval

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.DetectedBox
import com.manhwaviewer.data.ocr.MlKitTextRecognizer
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.image.cropRegion
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.inference.YoloRunner
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrEvalRunner {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newOcrEngine() = HayaiEngineFactory.fromAssets(context)

    @Test
    fun recognition() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val engine = newOcrEngine()
        val results = JSONObject()
        try {
            for (page in dataset.pages) {
                val image = decodeImage(page.imageFile.readBytes())
                for (item in page.items) {
                    // 앱의 YOLO 검출 결과와 같은 여백 규칙을 정답 박스에 적용한다.
                    val padded = YoloPostProcessor.addPadding(
                        DetectedBox(item.x, item.y, item.width, item.height, confidence = 1f, isJapanese = true),
                        image.width,
                        image.height,
                    )
                    val crop = cropToPlatformImage(image, padded.x, padded.y, padded.width, padded.height)
                    val start = SystemClock.elapsedRealtime()
                    val text = engine.recognizeText(crop)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    results.put("${page.name}/${item.id}", JSONObject().put("text", text).put("ms", elapsed))
                }
            }
        } finally {
            engine.close()
        }
        dataset.writeOutput("recognition.json", results)
        assertEquals(dataset.itemCount, results.length())
    }

    /**
     * The platform recognizer on the same ground-truth crops, as the reference for what the OCR
     * model has to match before horizontal text can be routed to it instead.
     */
    @Test
    fun recognitionPlatform() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val recognizer = MlKitTextRecognizer()
        val results = JSONObject()
        for (page in dataset.pages) {
            val image = decodeImage(page.imageFile.readBytes())
            for (item in page.items) {
                val padded = YoloPostProcessor.addPadding(
                    DetectedBox(item.x, item.y, item.width, item.height, confidence = 1f, isJapanese = true),
                    image.width,
                    image.height,
                )
                val crop = cropRegion(image, padded.x, padded.y, padded.width, padded.height)
                val start = SystemClock.elapsedRealtime()
                val text = recognizer.recognizeText(crop)
                val elapsed = SystemClock.elapsedRealtime() - start
                results.put("${page.name}/${item.id}", JSONObject().put("text", text).put("ms", elapsed))
            }
        }
        dataset.writeOutput("recognition-mlkit.json", results)
        assertEquals(dataset.itemCount, results.length())
    }

    @Test
    fun pipeline() = runBlocking {
        val dataset = EvalDataset.loadOrSkip(context)
        val engine = newOcrEngine()
        val repository = TextDetectionRepositoryImpl(
            CommonYoloTextDetector(YoloRunner(context)),
            verticalOcr = engine,
            platformRecognizer = MlKitTextRecognizer(),
        )
        val results = JSONObject()
        try {
            for (page in dataset.pages) {
                val start = SystemClock.elapsedRealtime()
                val blocks = repository.detectAndRecognize(page.imageFile.readBytes())
                val elapsed = SystemClock.elapsedRealtime() - start
                val blocksJson = JSONArray()
                blocks.forEach { block ->
                    blocksJson.put(
                        JSONObject()
                            .put("bbox", JSONArray(listOf(block.x, block.y, block.width, block.height)))
                            .put("text", block.text),
                    )
                }
                results.put(page.name, JSONObject().put("ms", elapsed).put("blocks", blocksJson))
            }
        } finally {
            engine.close()
        }
        dataset.writeOutput("pipeline.json", results)
        assertEquals(dataset.pages.size, results.length())
    }
}
