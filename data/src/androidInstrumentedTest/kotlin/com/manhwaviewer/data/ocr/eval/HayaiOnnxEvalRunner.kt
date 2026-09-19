package com.manhwaviewer.data.ocr.eval

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.DetectedBox
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.hayai.HayaiHost
import com.manhwaviewer.data.ocr.hayai.HayaiOcrEngine
import com.manhwaviewer.data.ocr.hayai.HayaiOnnxGraphs
import com.manhwaviewer.data.ocr.hayai.HayaiTokenizer
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
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

    /** The crop as HWC uint8, which is what the reference resizes. */
    private fun rgbOf(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rgb = IntArray(width * height * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            rgb[i * 3] = (p shr 16) and 0xFF
            rgb[i * 3 + 1] = (p shr 8) and 0xFF
            rgb[i * 3 + 2] = p and 0xFF
        }
        return rgb
    }

    @Test
    fun recognition() {
        val dataset = EvalDataset.loadOrSkip(context)
        val dir = modelDir()
        val graphs = HayaiOnnxGraphs(dir).apply { initialize() }
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        val engine = HayaiOcrEngine(
            graphs = graphs,
            tokenizer = tokenizer,
            positionBase = positionBase(dir),
            bosId = requireToken(tokenizer, "<bos>"),
            eosId = requireToken(tokenizer, "<eos>"),
            padId = requireToken(tokenizer, "<pad>"),
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
                    val (targetHeight, targetWidth) = HayaiHost.scaledSize(crop.height, crop.width)
                    val start = SystemClock.elapsedRealtime()
                    val resized = HayaiHost.resizeRgb(
                        rgbOf(crop), crop.height, crop.width, targetHeight, targetWidth,
                    )
                    val text = engine.recognize(
                        FloatArray(resized.size) { resized[it].toFloat() },
                        targetHeight / HayaiHost.PATCH_SIZE,
                        targetWidth / HayaiHost.PATCH_SIZE,
                    )
                    val elapsed = SystemClock.elapsedRealtime() - start
                    results.put(
                        "${page.name}/${item.id}",
                        JSONObject().put("text", text).put("ms", elapsed),
                    )
                }
            }
        } finally {
            graphs.close()
        }
        dataset.writeOutput("recognition.json", results)
        assertEquals(dataset.itemCount, results.length())
    }

    /** Looked up rather than hard-coded, as the reference does. */
    private fun requireToken(tokenizer: HayaiTokenizer, token: String): Int =
        tokenizer.tokenIdOf(token)
            ?: error("tokenizer.json carries no $token; the loop cannot start or stop without it")
}
