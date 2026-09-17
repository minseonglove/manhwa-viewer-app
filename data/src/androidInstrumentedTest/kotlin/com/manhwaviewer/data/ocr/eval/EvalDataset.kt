package com.manhwaviewer.data.ocr.eval

import android.content.Context
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.io.File

class EvalItem(val id: String, val x: Int, val y: Int, val width: Int, val height: Int)

class EvalPage(val name: String, val imageFile: File, val items: List<EvalItem>)

class EvalDataset private constructor(private val root: File, val pages: List<EvalPage>) {

    val itemCount: Int get() = pages.sumOf { it.items.size }

    fun writeOutput(fileName: String, json: JSONObject) {
        val outDir = File(root, "out").apply { mkdirs() }
        File(outDir, fileName).writeText(json.toString(2))
    }

    companion object {
        fun loadOrSkip(context: Context): EvalDataset {
            val root = File(context.filesDir, "ocr-eval")
            val gtFiles = File(root, "gt").listFiles { file -> file.extension == "json" }
                ?.sortedBy { it.name }
                .orEmpty()
            assumeTrue("No OCR eval data at ${root.absolutePath}", gtFiles.isNotEmpty())
            val pages = gtFiles.mapNotNull { parsePage(it, File(root, "pages")) }
            assumeTrue("Only draft pages at ${root.absolutePath}", pages.isNotEmpty())
            return EvalDataset(root, pages)
        }

        private fun parsePage(gtFile: File, pagesDir: File): EvalPage? {
            val json = JSONObject(gtFile.readText())
            if (json.optBoolean("draft", false)) return null
            val name = json.getString("page")
            val imageFile = File(pagesDir, "$name.jpg")
            check(imageFile.exists()) { "Missing page image ${imageFile.absolutePath}" }
            val itemsJson = json.getJSONArray("items")
            val items = (0 until itemsJson.length()).map { index ->
                val item = itemsJson.getJSONObject(index)
                val bbox = item.getJSONArray("bbox")
                EvalItem(item.getString("id"), bbox.getInt(0), bbox.getInt(1), bbox.getInt(2), bbox.getInt(3))
            }
            return EvalPage(name, imageFile, items)
        }
    }
}
