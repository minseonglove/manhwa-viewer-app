package com.manhwaviewer.presentation.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    return Image.makeFromEncoded(this).toComposeImageBitmap()
}

actual suspend fun getImageDimensions(bytes: ByteArray): Pair<Int, Int> =
    withContext(Dispatchers.Default) {
        val image = Image.makeFromEncoded(bytes)
        image.width to image.height
    }

@OptIn(ExperimentalForeignApi::class)
actual suspend fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray =
    withContext(Dispatchers.Default) {
        val image = Image.makeFromEncoded(bytes)
        val safeX = x.coerceIn(0, image.width - 1)
        val safeY = y.coerceIn(0, image.height - 1)
        val safeW = width.coerceIn(1, image.width - safeX)
        val safeH = height.coerceIn(1, image.height - safeY)

        val srcBitmap = Bitmap.makeFromImage(image)
        val info = ImageInfo(safeW, safeH, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val dstBitmap = Bitmap()
        dstBitmap.allocPixels(info)

        val srcPixels = srcBitmap.readPixels(
            ImageInfo(image.width, image.height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
        ) ?: return@withContext bytes

        val bytesPerPixel = 4
        val srcRowBytes = image.width * bytesPerPixel
        val dstRowBytes = safeW * bytesPerPixel
        val dstPixels = ByteArray(safeH * dstRowBytes)

        for (row in 0 until safeH) {
            val srcOffset = (safeY + row) * srcRowBytes + safeX * bytesPerPixel
            val dstOffset = row * dstRowBytes
            srcPixels.copyInto(dstPixels, dstOffset, srcOffset, srcOffset + dstRowBytes)
        }

        dstBitmap.installPixels(dstPixels)
        val croppedImage = Image.makeFromBitmap(dstBitmap)
        val encoded = croppedImage.encodeToData() ?: return@withContext bytes
        encoded.bytes
    }

actual suspend fun mergeImagesHorizontally(first: ByteArray, second: ByteArray): ByteArray =
    withContext(Dispatchers.Default) {
        val img1 = Image.makeFromEncoded(first)
        val img2 = Image.makeFromEncoded(second)

        val targetH = maxOf(img1.height, img2.height)
        val w1 = (img1.width.toFloat() * targetH / img1.height).toInt()
        val w2 = (img2.width.toFloat() * targetH / img2.height).toInt()

        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w1 + w2, targetH)
        val canvas = surface.canvas

        canvas.drawImageRect(
            img1,
            org.jetbrains.skia.Rect.makeWH(img1.width.toFloat(), img1.height.toFloat()),
            org.jetbrains.skia.Rect.makeXYWH(0f, 0f, w1.toFloat(), targetH.toFloat()),
        )
        canvas.drawImageRect(
            img2,
            org.jetbrains.skia.Rect.makeWH(img2.width.toFloat(), img2.height.toFloat()),
            org.jetbrains.skia.Rect.makeXYWH(w1.toFloat(), 0f, w2.toFloat(), targetH.toFloat()),
        )

        val snapshot = surface.makeImageSnapshot()
        val encoded = snapshot.encodeToData() ?: return@withContext first
        encoded.bytes
    }
