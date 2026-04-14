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

actual fun computeBorderAverageColor(image: PlatformImage): Float {
    val w = image.width
    val h = image.height
    if (w == 0 || h == 0) return 1.0f

    var sum = 0L
    var count = 0
    val xStep = maxOf(1, w / 50)
    val yStep = maxOf(1, h / 50)

    // Top and bottom rows
    for (y in intArrayOf(0, h - 1)) {
        var x = 0
        while (x < w) {
            val pixel = image.getPixel(x, y)
            sum += ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
            count++
            x += xStep
        }
    }
    // Left and right columns
    for (x in intArrayOf(0, w - 1)) {
        var y = 0
        while (y < h) {
            val pixel = image.getPixel(x, y)
            sum += ((pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF) + (pixel and 0xFF)) / 3
            count++
            y += yStep
        }
    }

    return if (count > 0) (sum.toFloat() / count / 255f).coerceIn(0f, 1f) else 1.0f
}

actual fun cropToPlatformImage(
    image: PlatformImage,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): PlatformImage = Bitmap.createBitmap(image, x, y, width, height)

actual fun getImageDimensions(image: PlatformImage): Pair<Int, Int> {
    return image.width to image.height
}
