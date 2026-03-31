package com.manhwaviewer.presentation.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

actual fun getImageDimensions(bytes: ByteArray): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return opts.outWidth to opts.outHeight
}

actual fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray {
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
    return stream.toByteArray()
}
