package com.manhwaviewer.data.overlay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Environment
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.manhwaviewer.data.BuildConfig
import com.manhwaviewer.data.db.TranslationCacheDao
import com.manhwaviewer.data.db.TranslationCacheEntity
import com.manhwaviewer.data.ocr.AndroidMangaOcrEngine
import com.manhwaviewer.data.ocr.AndroidPaddleOcrRecognizer
import com.manhwaviewer.data.ocr.CtdTextDetector
import com.manhwaviewer.data.ocr.TextDetectionRepositoryImpl
import com.manhwaviewer.data.translation.PapagoTranslationService
import com.manhwaviewer.data.translation.TranslationRepositoryImpl
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class TranslationOverlayRenderTest {

    companion object {
        private const val TAG = "OverlayRenderTest"
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun renderTranslationOverlayAndSaveToFile() = runTest {
        // 1. 테스트 이미지 로드
        val imageBytes = InstrumentationRegistry.getInstrumentation()
            .context.assets.open("test/06.png").readBytes()
        Log.d(TAG, "Image loaded: ${imageBytes.size} bytes")

        // 2. OCR 실행 (실제 엔진)
        val ocrRepo = TextDetectionRepositoryImpl(
            CtdTextDetector(context),
            verticalOcr = AndroidMangaOcrEngine(context),
            horizontalOcr = AndroidPaddleOcrRecognizer(context),
        )

        val ocrStart = System.currentTimeMillis()
        val textBlocks = ocrRepo.detectAndRecognize(imageBytes)
        val ocrElapsed = System.currentTimeMillis() - ocrStart

        Log.d(TAG, "OCR completed: ${textBlocks.size} blocks in ${ocrElapsed}ms")
        textBlocks.forEachIndexed { i, block ->
            Log.d(TAG, "  [$i] (${block.x},${block.y} ${block.width}x${block.height}) \"${block.text}\"")
        }
        assertTrue("OCR should detect at least one text block", textBlocks.isNotEmpty())

        // 3. 번역 실행 (실제 Papago API)
        val translationRepo = TranslationRepositoryImpl(
            papagoService = PapagoTranslationService(
                BuildConfig.PAPAGO_CLIENT_ID,
                BuildConfig.PAPAGO_CLIENT_SECRET,
            ),
            cacheDao = InMemoryCacheDao(),
        )

        val translateStart = System.currentTimeMillis()
        val translationResults = translationRepo.translateBatch(textBlocks.map { it.text })
        val translateElapsed = System.currentTimeMillis() - translateStart

        Log.d(TAG, "Translation completed in ${translateElapsed}ms")
        translationResults.forEachIndexed { i, tr ->
            Log.d(TAG, "  [$i] \"${tr.originalText}\" → \"${tr.translatedText}\"")
        }

        // 4. 오버레이 렌더링
        val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val overlayBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(overlayBitmap)

        val minCount = minOf(textBlocks.size, translationResults.size)
        for (i in 0 until minCount) {
            val block = textBlocks[i]
            val translation = translationResults[i]
            if (translation.translatedText.isBlank()) continue

            val bgColor = extractDominantColor(originalBitmap, block)
            drawOverlayBlock(canvas, block, translation.translatedText, bgColor)
        }

        // 5. 결과 이미지 저장 (/sdcard/Download — 테스트 APK 제거 후에도 유지)
        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "overlay_test",
        )
        outputDir.mkdirs()

        val outputFile = File(outputDir, "overlay_result_06.png")
        FileOutputStream(outputFile).use { fos ->
            overlayBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }

        Log.d(TAG, "=== OVERLAY RENDER COMPLETE ===")
        Log.d(TAG, "Output: ${outputFile.absolutePath}")
        Log.d(TAG, "Size: ${outputFile.length()} bytes")
        Log.d(TAG, "Dimensions: ${overlayBitmap.width}x${overlayBitmap.height}")
        Log.d(TAG, "================================")

        assertTrue("Output file should exist", outputFile.exists())
        assertTrue("Output file should not be empty", outputFile.length() > 0)

        originalBitmap.recycle()
        overlayBitmap.recycle()
    }

    @Test
    fun renderOverlayForMultipleImages() = runTest {
        val testImages = listOf("test/05.png", "test/06.png", "test/07.png")

        val ocrRepo = TextDetectionRepositoryImpl(
            CtdTextDetector(context),
            verticalOcr = AndroidMangaOcrEngine(context),
            horizontalOcr = AndroidPaddleOcrRecognizer(context),
        )
        val translationRepo = TranslationRepositoryImpl(
            papagoService = PapagoTranslationService(
                BuildConfig.PAPAGO_CLIENT_ID,
                BuildConfig.PAPAGO_CLIENT_SECRET,
            ),
            cacheDao = InMemoryCacheDao(),
        )

        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "overlay_test",
        )
        outputDir.mkdirs()

        for (assetPath in testImages) {
            val fileName = assetPath.substringAfterLast("/").substringBeforeLast(".")
            Log.d(TAG, "--- Processing $assetPath ---")

            val imageBytes = InstrumentationRegistry.getInstrumentation()
                .context.assets.open(assetPath).readBytes()

            val textBlocks = ocrRepo.detectAndRecognize(imageBytes)
            Log.d(TAG, "  OCR: ${textBlocks.size} blocks")

            if (textBlocks.isEmpty()) {
                Log.d(TAG, "  Skipped: no text blocks detected")
                continue
            }

            val translations = translationRepo.translateBatch(textBlocks.map { it.text })
            Log.d(TAG, "  Translation: ${translations.size} results")

            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val overlayBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(overlayBitmap)

            val minCount = minOf(textBlocks.size, translations.size)
            for (i in 0 until minCount) {
                val block = textBlocks[i]
                val translation = translations[i]
                if (translation.translatedText.isBlank()) continue
                val bgColor = extractDominantColor(originalBitmap, block)
                drawOverlayBlock(canvas, block, translation.translatedText, bgColor)
            }

            val outputFile = File(outputDir, "overlay_result_$fileName.png")
            FileOutputStream(outputFile).use { fos ->
                overlayBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            Log.d(TAG, "  Saved: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            originalBitmap.recycle()
            overlayBitmap.recycle()
        }

        Log.d(TAG, "=== ALL OVERLAY RENDERS COMPLETE ===")
    }

    // ── 도미넌트 컬러 추출 (Android Bitmap API) ───────────────

    private fun extractDominantColor(bitmap: Bitmap, block: TextBlock): Int {
        val safeX = block.x.coerceIn(0, bitmap.width - 1)
        val safeY = block.y.coerceIn(0, bitmap.height - 1)
        val safeRight = (block.x + block.width).coerceIn(safeX + 1, bitmap.width)
        val safeBottom = (block.y + block.height).coerceIn(safeY + 1, bitmap.height)
        val w = safeRight - safeX
        val h = safeBottom - safeY

        val stride = when {
            w * h > 10000 -> 4
            w * h > 2500 -> 3
            else -> 2
        }

        // 5비트 양자화 — flat array
        val bucketSize = 32768
        val data = IntArray(bucketSize * 4)

        for (dy in 0 until h step stride) {
            for (dx in 0 until w step stride) {
                val pixel = bitmap.getPixel(safeX + dx, safeY + dy)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                val base = key * 4
                data[base]++
                data[base + 1] += r
                data[base + 2] += g
                data[base + 3] += b
            }
        }

        var bestCount = 0
        var bestIdx = 0
        for (i in 0 until bucketSize) {
            val count = data[i * 4]
            if (count > bestCount) {
                bestCount = count
                bestIdx = i
            }
        }

        if (bestCount == 0) return 0xFF808080.toInt()

        val base = bestIdx * 4
        val r = data[base + 1] / bestCount
        val g = data[base + 2] / bestCount
        val b = data[base + 3] / bestCount
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ── 오버레이 블록 렌더링 (Android Canvas API) ─────────────

    private fun drawOverlayBlock(
        canvas: Canvas,
        block: TextBlock,
        translatedText: String,
        bgColor: Int,
    ) {
        val rect = RectF(
            block.x.toFloat(),
            block.y.toFloat(),
            (block.x + block.width).toFloat(),
            (block.y + block.height).toFloat(),
        )
        val cornerRadius = minOf(8f, block.width * 0.1f, block.height * 0.1f)
        val fadeMargin = minOf(6f, block.width * 0.08f, block.height * 0.08f)

        // 1. 둥근 모서리 배경
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // 2. 페이드 아웃 가장자리
        if (fadeMargin > 1f) {
            val transparent = bgColor and 0x00FFFFFF // alpha = 0
            drawFadeEdges(canvas, rect, fadeMargin, bgColor, transparent)
        }

        // 3. 텍스트 자동 크기 조정 + 렌더링
        val textColor = contrastingTextColor(bgColor)
        val padding = 4f
        val availableW = block.width - padding * 2
        val availableH = block.height - padding * 2
        if (availableW <= 0 || availableH <= 0) return

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val bestSize = findBestFontSize(textPaint, translatedText, availableW, availableH)
        textPaint.textSize = bestSize

        // 여러 줄로 분할해서 중앙 정렬
        val lines = wrapText(textPaint, translatedText, availableW)
        val lineHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val totalTextHeight = lineHeight * lines.size
        val startY = rect.top + padding + (availableH - totalTextHeight) / 2f - textPaint.fontMetrics.ascent
        val centerX = rect.left + padding + availableW / 2f

        for ((lineIdx, line) in lines.withIndex()) {
            val y = startY + lineIdx * lineHeight
            canvas.drawText(line, centerX, y, textPaint)
        }
    }

    private fun drawFadeEdges(
        canvas: Canvas,
        rect: RectF,
        margin: Float,
        solidColor: Int,
        transparentColor: Int,
    ) {
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 상단
        fadePaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.top + margin,
            transparentColor, solidColor, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + margin, fadePaint)

        // 하단
        fadePaint.shader = LinearGradient(
            0f, rect.bottom - margin, 0f, rect.bottom,
            solidColor, transparentColor, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect.left, rect.bottom - margin, rect.right, rect.bottom, fadePaint)

        // 좌측
        fadePaint.shader = LinearGradient(
            rect.left, 0f, rect.left + margin, 0f,
            transparentColor, solidColor, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect.left, rect.top, rect.left + margin, rect.bottom, fadePaint)

        // 우측
        fadePaint.shader = LinearGradient(
            rect.right - margin, 0f, rect.right, 0f,
            solidColor, transparentColor, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect.right - margin, rect.top, rect.right, rect.bottom, fadePaint)
    }

    private fun findBestFontSize(
        paint: Paint,
        text: String,
        availableW: Float,
        availableH: Float,
    ): Float {
        var lo = 8f
        var hi = 64f
        var best = lo

        repeat(12) {
            val mid = (lo + hi) / 2f
            paint.textSize = mid
            val lines = wrapText(paint, text, availableW)
            val lineHeight = paint.fontMetrics.let { it.descent - it.ascent }
            val totalHeight = lineHeight * lines.size

            if (totalHeight <= availableH) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        return best
    }

    private fun wrapText(paint: Paint, text: String, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.toList().map { it.toString() } // 문자 단위 (한/일/중)
        if (words.isEmpty()) return listOf("")

        var currentLine = StringBuilder()
        for (char in words) {
            val testLine = currentLine.toString() + char
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(char)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(char)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun contrastingTextColor(bgColor: Int): Int {
        val r = (bgColor shr 16) and 0xFF
        val g = (bgColor shr 8) and 0xFF
        val b = bgColor and 0xFF
        val luminance = 0.299f * r / 255f + 0.587f * g / 255f + 0.114f * b / 255f
        return if (luminance > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}

// ── 인메모리 캐시 DAO ─────────────────────────────────────────

private class InMemoryCacheDao : TranslationCacheDao {
    private val cache = mutableListOf<TranslationCacheEntity>()

    override suspend fun findCache(
        sourceText: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslationCacheEntity? {
        return cache.find {
            it.sourceText == sourceText &&
                it.sourceLanguage == sourceLanguage &&
                it.targetLanguage == targetLanguage
        }
    }

    override suspend fun insertCache(entity: TranslationCacheEntity) {
        cache.removeAll {
            it.sourceText == entity.sourceText &&
                it.sourceLanguage == entity.sourceLanguage &&
                it.targetLanguage == entity.targetLanguage
        }
        cache.add(entity)
    }

    override suspend fun findCacheByTexts(
        sourceTexts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<TranslationCacheEntity> {
        return cache.filter {
            it.sourceText in sourceTexts &&
                it.sourceLanguage == sourceLanguage &&
                it.targetLanguage == targetLanguage
        }
    }

    override suspend fun clearAll() = cache.clear()
    override suspend fun getCacheCount(): Int = cache.size
}
