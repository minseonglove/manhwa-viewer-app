package com.manhwaviewer.data.ocr.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

actual typealias PlatformImage = Bitmap

actual fun decodeImage(bytes: ByteArray): PlatformImage {
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalArgumentException("Failed to decode image bytes")
}

actual fun resizeAndPadToFloatArray(
    image: PlatformImage,
    targetSize: Int,
    normRange: NormRange,
    padColor: Float,
): FloatArray {
    val scale = targetSize.toFloat() / maxOf(image.width, image.height)
    val scaledW = (image.width * scale).toInt().coerceAtLeast(1)
    val scaledH = (image.height * scale).toInt().coerceAtLeast(1)
    val left = (targetSize - scaledW) / 2
    val top = (targetSize - scaledH) / 2

    val scaled = Bitmap.createScaledBitmap(image, scaledW, scaledH, true)

    val padded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val colorInt = (padColor * 255).toInt().coerceIn(0, 255)
    padded.eraseColor(Color.rgb(colorInt, colorInt, colorInt))
    val canvas = Canvas(padded)
    canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
    if (scaled !== image) scaled.recycle()

    val pixels = IntArray(targetSize * targetSize)
    padded.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
    padded.recycle()

    val area = targetSize * targetSize
    val result = FloatArray(3 * area)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16 and 0xFF)
        val g = (pixel shr 8 and 0xFF)
        val b = (pixel and 0xFF)
        when (normRange) {
            NormRange.ZERO_ONE -> {
                result[i] = r / 255.0f
                result[area + i] = g / 255.0f
                result[2 * area + i] = b / 255.0f
            }
            NormRange.MINUS_ONE_ONE -> {
                result[i] = r / 127.5f - 1.0f
                result[area + i] = g / 127.5f - 1.0f
                result[2 * area + i] = b / 127.5f - 1.0f
            }
        }
    }
    return result
}

actual fun cropRegion(
    image: PlatformImage,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ByteArray {
    val cropped = Bitmap.createBitmap(image, x, y, width, height)
    val baos = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
    if (cropped !== image) cropped.recycle()
    return baos.toByteArray()
}

actual fun getImageDimensions(image: PlatformImage): Pair<Int, Int> {
    return image.width to image.height
}
