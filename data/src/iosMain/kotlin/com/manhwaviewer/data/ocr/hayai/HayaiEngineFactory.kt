package com.manhwaviewer.data.ocr.hayai

import com.manhwaviewer.data.ocr.MangaOcrEngine
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreML.MLComputeUnits
import platform.CoreML.MLComputeUnitsCPUAndNeuralEngine
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy

object HayaiEngineFactory {

    private const val POSITION_BASE_FLOATS = 16 * 16 * 768

    fun fromBundle(
        subdirectory: String = "hayai",
        computeUnits: MLComputeUnits = MLComputeUnitsCPUAndNeuralEngine,
    ): MangaOcrEngine {
        val tokenizer = HayaiTokenizer.fromTokenizerJson(readText("tokenizer", "json", subdirectory))
        fun id(token: String) = tokenizer.tokenIdOf(token) ?: error("tokenizer.json carries no $token")
        return HayaiMangaOcrEngine(
            HayaiOcrEngine(
                graphs = HayaiCoreMLGraphs(subdirectory).apply { initialize(computeUnits) },
                tokenizer = tokenizer,
                positionBase = positionBase(subdirectory),
                bosId = id("<bos>"), eosId = id("<eos>"), padId = id("<pad>"),
            )
        )
    }

    private fun resourcePath(name: String, type: String, subdirectory: String): String =
        NSBundle.mainBundle.pathForResource(name, ofType = type, inDirectory = subdirectory)
            ?: error("$name.$type not found in bundle subdirectory '$subdirectory'")

    private fun readText(name: String, type: String, subdirectory: String): String =
        NSString.stringWithContentsOfFile(resourcePath(name, type, subdirectory), NSUTF8StringEncoding, null)?.toString()
            ?: error("failed to read $name.$type")

    // The file is little-endian float32, which is also the byte order of every device this runs on.
    private fun positionBase(subdirectory: String): FloatArray {
        val data = NSData.dataWithContentsOfFile(resourcePath("position_base", "f32", subdirectory))
            ?: error("failed to read position_base.f32")
        require(data.length.toInt() == POSITION_BASE_FLOATS * 4) { "position_base.f32 holds ${data.length.toInt() / 4} floats" }
        return FloatArray(POSITION_BASE_FLOATS).also { floats ->
            floats.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
    }
}
