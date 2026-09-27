package com.manhwaviewer.data.ocr.hayai

import android.content.Context
import com.manhwaviewer.data.ocr.MangaOcrEngine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object HayaiEngineFactory {

    /** Bump whenever a file in [HayaiModelStager.FILES] changes, or installs keep running the set they staged earlier. */
    const val MODEL_VERSION = "litert-encoder-onnx-decoder-1"

    private const val POSITION_BASE_FLOATS = 16 * 16 * 768

    /**
     * The app's engine: the encoder on LiteRT (GPU if it passes its self-check, else CPU) and
     * prefill/decode on ORT from the staged assets. [preferGpu] false forces the CPU fallback.
     */
    fun fromAssets(context: Context, preferGpu: Boolean = true): MangaOcrEngine {
        val dir = HayaiModelStager.stage(context.assets::open, File(context.cacheDir, HayaiModelStager.ASSET_DIR), MODEL_VERSION)
        val positionBase = positionBase(dir)
        val encoder = HayaiLiteRtEncoder(context, positionBase, preferGpu)
        val decoder = HayaiOnnxGraphs(dir)
        return closingOnFailure(encoder::close, decoder::close) {
            decoder.initialize(loadEncoder = false)
            engine(HayaiHybridGraphs(encoder, decoder), dir, positionBase)
        }
    }

    internal fun engine(graphs: HayaiGraphs, dir: File, positionBase: FloatArray): MangaOcrEngine {
        val tokenizer = HayaiTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        fun id(token: String) = tokenizer.tokenIdOf(token) ?: error("tokenizer.json carries no $token")
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = graphs,
                tokenizer = tokenizer,
                positionBase = positionBase,
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    internal fun positionBase(modelDir: File): FloatArray {
        val bytes = File(modelDir, "position_base.f32").readBytes()
        require(bytes.size == POSITION_BASE_FLOATS * 4) { "position_base.f32 holds ${bytes.size / 4} floats" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(POSITION_BASE_FLOATS).also { buffer.get(it) }
    }
}

/**
 * [block], closing everything in [close] if it throws. A build that fails must not leave open what
 * it opened: the caller builds again on its next use, and the LiteRT encoder alone holds the
 * model's weights on the GPU.
 */
internal inline fun <T> closingOnFailure(vararg close: () -> Unit, block: () -> T): T =
    try {
        block()
    } catch (t: Throwable) {
        for (c in close) {
            try {
                c()
            } catch (e: Throwable) {
                t.addSuppressed(e)
            }
        }
        throw t
    }
