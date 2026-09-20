package com.manhwaviewer.data.ocr.hayai

import java.io.File
import java.io.InputStream

/**
 * Copies the Hayai model set out of the APK into one directory ORT can open by path.
 *
 * The decoder graphs keep their weights in `hayai_decoder_shared.data`, which ORT resolves
 * relative to the `.onnx` file, so the files cannot be loaded from asset streams one by one.
 */
object HayaiModelStager {

    const val ASSET_DIR = "hayai"
    const val VERSION_FILE = ".version"

    val FILES: List<String> = listOf(
        "hayai_encoder.onnx",
        "hayai_prefill.onnx",
        "hayai_decode.onnx",
        "hayai_decoder_shared.data",
        "position_base.f32",
        "tokenizer.json",
    )

    /**
     * The version marker is written last, so a directory without a matching marker is either
     * another model set or a copy that never finished. Both are staged again from scratch:
     * a file left over from an older set has the right name and the wrong weights.
     */
    fun stage(open: (String) -> InputStream, targetDir: File, version: String): File {
        val marker = File(targetDir, VERSION_FILE)
        val staged = marker.isFile && marker.readText() == version && FILES.all { File(targetDir, it).isFile }
        if (staged) return targetDir

        targetDir.mkdirs()
        marker.delete()
        for (name in FILES) {
            val tmp = File(targetDir, "$name.tmp")
            open("$ASSET_DIR/$name").use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            val file = File(targetDir, name)
            file.delete()
            check(tmp.renameTo(file)) { "could not move ${tmp.name} into place" }
        }
        marker.writeText(version)
        return targetDir
    }
}
