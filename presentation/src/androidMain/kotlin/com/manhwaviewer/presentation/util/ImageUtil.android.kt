package com.manhwaviewer.presentation.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_BITMAP_BYTES = 100_000_000L // 100MB Canvas limit

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val opts = BitmapFactory.Options()
    opts.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(this, 0, this.size, opts)

    val pixelBytes = opts.outWidth.toLong() * opts.outHeight.toLong() * 4
    var sampleSize = 1
    while (pixelBytes / (sampleSize * sampleSize) > MAX_BITMAP_BYTES) {
        sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options()
    decodeOpts.inSampleSize = sampleSize
    val bitmap = BitmapFactory.decodeByteArray(this, 0, this.size, decodeOpts)
    return bitmap.asImageBitmap()
}

actual suspend fun getImageDimensions(bytes: ByteArray): Pair<Int, Int> =
    withContext(Dispatchers.Default) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        opts.outWidth to opts.outHeight
    }

actual suspend fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray =
    withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val safeX = x.coerceIn(0, bitmap.width - 1)
        val safeY = y.coerceIn(0, bitmap.height - 1)
        val safeW = width.coerceIn(1, bitmap.width - safeX)
        val safeH = height.coerceIn(1, bitmap.height - safeY)
        val cropped = Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
        val stream = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)
        if (cropped !== bitmap) cropped.recycle()
        bitmap.recycle()
        stream.toByteArray()
    }

actual suspend fun mergeImagesHorizontally(first: ByteArray, second: ByteArray): ByteArray =
    withContext(Dispatchers.Default) {
        val bmp1 = BitmapFactory.decodeByteArray(first, 0, first.size)
        val bmp2 = BitmapFactory.decodeByteArray(second, 0, second.size)

        val targetH = maxOf(bmp1.height, bmp2.height)
        val w1 = (bmp1.width.toFloat() * targetH / bmp1.height).toInt()
        val w2 = (bmp2.width.toFloat() * targetH / bmp2.height).toInt()

        val merged = Bitmap.createBitmap(w1 + w2, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(merged)

        val scaled1 = Bitmap.createScaledBitmap(bmp1, w1, targetH, true)
        val scaled2 = Bitmap.createScaledBitmap(bmp2, w2, targetH, true)

        canvas.drawBitmap(scaled1, 0f, 0f, null)
        canvas.drawBitmap(scaled2, w1.toFloat(), 0f, null)

        val stream = ByteArrayOutputStream()
        merged.compress(Bitmap.CompressFormat.JPEG, 95, stream)

        if (scaled1 !== bmp1) scaled1.recycle()
        if (scaled2 !== bmp2) scaled2.recycle()
        bmp1.recycle()
        bmp2.recycle()
        merged.recycle()

        stream.toByteArray()
    }
