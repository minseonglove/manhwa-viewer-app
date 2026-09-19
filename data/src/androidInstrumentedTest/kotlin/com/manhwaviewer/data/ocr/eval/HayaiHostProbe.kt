package com.manhwaviewer.data.ocr.eval

import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.ocr.DetectedBox
import com.manhwaviewer.data.ocr.YoloPostProcessor
import com.manhwaviewer.data.ocr.hayai.HayaiHost
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.readRgbHwc
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

/**
 * Dumps the host stage for a few items so it can be bisected against the reference.
 *
 * The device run differs from the reference on 137 of 499 items with plausible-looking
 * alternatives rather than garbage, which points at small numeric differences amplified by
 * greedy decoding rather than a structural error. This narrows where they enter: the decoded
 * crop, the resize, or the tensors built from it.
 */
class HayaiHostProbe {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sample(values: IntArray, count: Int = 12): JSONArray {
        val out = JSONArray()
        for (k in 0 until count) {
            val i = (values.size - 1).toLong() * k / (count - 1)
            out.put(JSONArray(listOf(i.toInt(), values[i.toInt()])))
        }
        return out
    }

    private fun sample(values: FloatArray, count: Int = 12): JSONArray {
        val out = JSONArray()
        for (k in 0 until count) {
            val i = (values.size - 1).toLong() * k / (count - 1)
            out.put(JSONArray(listOf(i.toInt(), values[i.toInt()].toDouble())))
        }
        return out
    }

    private fun positionBase(): FloatArray {
        val file = File(File(File(context.filesDir, "ocr-eval"), "hayai-onnx"), "position_base.f32")
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(bytes.size / 4).also { buffer.get(it) }
    }

    @Test
    fun dumpHostStage() {
        val dataset = EvalDataset.loadOrSkip(context)
        val wanted = setOf("city01_p002/t02", "city01_p002/t12", "city01_p002/t15")
        val out = JSONObject()
        for (page in dataset.pages) {
            val image = decodeImage(page.imageFile.readBytes())
            for (item in page.items) {
                val key = "${page.name}/${item.id}"
                if (key !in wanted) continue
                val padded = YoloPostProcessor.addPadding(
                    DetectedBox(item.x, item.y, item.width, item.height,
                        confidence = 1f, isJapanese = true),
                    image.width, image.height,
                )
                val crop = cropToPlatformImage(image, padded.x, padded.y, padded.width, padded.height)
                val rgb = readRgbHwc(crop)
                val (th, tw) = HayaiHost.scaledSize(crop.height, crop.width)
                val resized = HayaiHost.resizeRgb(rgb, crop.height, crop.width, th, tw)
                val pixels = HayaiHost.encoderInput(
                    FloatArray(resized.size) { resized[it].toFloat() }, th / 16, tw / 16,
                )
                val (cos, sin) = HayaiHost.rope(th / 16, tw / 16)
                val posEmbeds = HayaiHost.positionEmbeddings(positionBase(), th / 16, tw / 16)
                val attn = HayaiHost.attentionMask(th / 16, tw / 16)
                out.put(key, JSONObject()
                    .put("posEmbedsSum", posEmbeds.sumOf { it.toDouble() })
                    .put("posEmbedsSample", sample(posEmbeds))
                    .put("cosSample", sample(cos))
                    .put("sinSample", sample(sin))
                    .put("attnOnes", attn.count { it == 1 })
                    .put("box", JSONArray(listOf(padded.x, padded.y, padded.width, padded.height)))
                    .put("cropSize", JSONArray(listOf(crop.width, crop.height)))
                    .put("target", JSONArray(listOf(tw, th)))
                    .put("cropSum", rgb.sumOf { it.toLong() })
                    .put("cropSample", sample(rgb))
                    .put("resizedSum", resized.sumOf { it.toLong() })
                    .put("resizedSample", sample(resized))
                    .put("pixelSample", sample(pixels)))
            }
        }
        dataset.writeOutput("host-probe.json", out)
    }
}
