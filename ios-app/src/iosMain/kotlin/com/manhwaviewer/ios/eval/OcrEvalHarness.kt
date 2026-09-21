package com.manhwaviewer.ios.eval

import com.manhwaviewer.data.ocr.AppleVisionTextRecognizer
import com.manhwaviewer.data.ocr.CommonYoloTextDetector
import com.manhwaviewer.data.ocr.MangaOcrEngine
import com.manhwaviewer.data.ocr.PlatformTextRecognizer
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.ocr.hayai.HayaiEngineFactory
import com.manhwaviewer.data.ocr.inference.YoloRunner
import kotlin.time.TimeSource
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The page-level A/B of Android's `HayaiPageTimingRunner`, for a device: the app as it is (Apple
 * Vision reads wide regions) against the OCR model reading every region on its own, with the
 * models and compute units the app ships.
 *
 * Called from the bench app target, never from the app itself. Results are rewritten after every
 * page so an interrupted run keeps what it measured.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object OcrEvalHarness {

    private fun repository(yolo: YoloRunner, engine: MangaOcrEngine, platformRecognizer: PlatformTextRecognizer?) =
        TextDetectionRepositoryImpl(
            CommonYoloTextDetector(yolo),
            verticalOcr = engine,
            platformRecognizer = platformRecognizer,
        )

    private fun readBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: error("cannot read $path")
        return ByteArray(data.length.toInt()).also { bytes ->
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
    }

    private fun write(path: String, text: String) {
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    /** Blocks until every page has run. Returns a one-line summary; failures are written to `error.txt`. */
    fun runPipelineAb(evalDir: String, outDir: String): String {
        val files = NSFileManager.defaultManager
        files.createDirectoryAtPath(outDir, withIntermediateDirectories = true, attributes = null, error = null)
        return try {
            val pages = (files.contentsOfDirectoryAtPath("$evalDir/pages", null) ?: emptyList<Any?>())
                .map { it.toString() }
                .filter { it.endsWith(".jpg") }
                .sorted()
            check(pages.isNotEmpty()) { "no pages under $evalDir/pages" }

            val yolo = YoloRunner()
            val engine = HayaiEngineFactory.fromBundle()
            val repos = linkedMapOf(
                "platform" to repository(yolo, engine, AppleVisionTextRecognizer()),
                "ocronly" to repository(yolo, engine, platformRecognizer = null),
            )
            val names = repos.keys.toList()
            val pipelines = names.associateWith { mutableMapOf<String, JsonObject>() }

            runBlocking {
                try {
                    val warmUp = readBytes("$evalDir/pages/${pages.first()}")
                    for (repo in repos.values) repo.detectAndRecognize(warmUp)

                    for ((index, file) in pages.withIndex()) {
                        val bytes = readBytes("$evalDir/pages/$file")
                        val shift = index % names.size
                        for (name in names.drop(shift) + names.take(shift)) {
                            val mark = TimeSource.Monotonic.markNow()
                            val blocks = repos.getValue(name).detectAndRecognize(bytes)
                            val ms = mark.elapsedNow().inWholeMilliseconds
                            pipelines.getValue(name)[file.removeSuffix(".jpg")] = buildJsonObject {
                                put("ms", JsonPrimitive(ms))
                                put(
                                    "blocks",
                                    JsonArray(
                                        blocks.map { b ->
                                            buildJsonObject {
                                                put("bbox", JsonArray(listOf(b.x, b.y, b.width, b.height).map { JsonPrimitive(it) }))
                                                put("text", JsonPrimitive(b.text))
                                            }
                                        },
                                    ),
                                )
                            }
                        }
                        for ((name, pipeline) in pipelines) write("$outDir/pipeline-$name.json", JsonObject(pipeline).toString())
                        write("$outDir/progress.txt", "${index + 1}/${pages.size}")
                    }
                } finally {
                    engine.close()
                }
            }
            "done: ${pages.size} pages".also { write("$outDir/done.txt", it) }
        } catch (e: Throwable) {
            val message = "${e::class.simpleName}: ${e.message}"
            write("$outDir/error.txt", message + "\n" + e.stackTraceToString().take(4000))
            "failed: $message"
        }
    }
}
