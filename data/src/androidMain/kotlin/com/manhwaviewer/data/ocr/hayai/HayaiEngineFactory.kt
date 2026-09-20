package com.manhwaviewer.data.ocr.hayai

import android.content.Context
import com.manhwaviewer.data.ocr.MangaOcrEngine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object HayaiEngineFactory {

    /** Bump whenever a file under `assets/hayai` changes, or installs keep running the set they staged earlier. */
    const val MODEL_VERSION = "onnx-int8-shared-1"

    private const val POSITION_BASE_FLOATS = 16 * 16 * 768

    fun fromAssets(context: Context): MangaOcrEngine =
        fromDirectory(HayaiModelStager.stage(context.assets::open, File(context.cacheDir, HayaiModelStager.ASSET_DIR), MODEL_VERSION))

    fun fromDirectory(modelDir: File): MangaOcrEngine {
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(modelDir, "tokenizer.json").readText())
        fun id(token: String) = tokenizer.tokenIdOf(token) ?: error("tokenizer.json carries no $token")
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = HayaiOnnxGraphs(modelDir).apply { initialize() },
                tokenizer = tokenizer,
                positionBase = positionBase(modelDir),
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    private fun positionBase(modelDir: File): FloatArray {
        val bytes = File(modelDir, "position_base.f32").readBytes()
        require(bytes.size == POSITION_BASE_FLOATS * 4) { "position_base.f32 holds ${bytes.size / 4} floats" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(POSITION_BASE_FLOATS).also { buffer.get(it) }
    }
}
