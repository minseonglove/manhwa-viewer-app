package com.manhwaviewer.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitTextRecognizer : PlatformTextRecognizer {

    companion object {
        private const val WIDE_RATIO_THRESHOLD = 3.0f
    }

    private val recognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return ""

        if (bitmap.width < 32 || bitmap.height < 32) {
            val w = maxOf(bitmap.width, 32)
            val h = maxOf(bitmap.height, 32)
            val padded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            padded.eraseColor(Color.WHITE)
            Canvas(padded).drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()
            bitmap = padded
        }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()
        bitmap.recycle()
        return result.text
    }

    override suspend fun detectHorizontalText(imageBytes: ByteArray): List<RecognizedHorizontalRegion> {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return emptyList()

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).await()
        bitmap.recycle()

        return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val rect = line.boundingBox ?: return@mapNotNull null
            val w = rect.width()
            val h = rect.height()
            if (h <= 0 || w.toFloat() / h < WIDE_RATIO_THRESHOLD) return@mapNotNull null

            RecognizedHorizontalRegion(
                x = rect.left.coerceAtLeast(0),
                y = rect.top.coerceAtLeast(0),
                width = w,
                height = h,
                text = line.text,
            )
        }
    }
}
