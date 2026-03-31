package com.manhwaviewer.presentation.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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

actual fun getImageDimensions(bytes: ByteArray): Pair<Int, Int> {
    val image = Image.makeFromEncoded(bytes)
    return image.width to image.height
}

@OptIn(ExperimentalForeignApi::class)
actual fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray {
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
    ) ?: return bytes

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
    val encoded = croppedImage.encodeToData() ?: return bytes
    return encoded.bytes
}
