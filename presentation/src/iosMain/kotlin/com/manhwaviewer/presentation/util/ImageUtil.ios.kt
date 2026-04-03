package com.manhwaviewer.presentation.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import kotlin.math.sqrt

private const val MAX_BITMAP_BYTES = 100_000_000L // 100MB

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val image = Image.makeFromEncoded(this)
    val pixelBytes = image.width.toLong() * image.height.toLong() * 4
    if (pixelBytes <= MAX_BITMAP_BYTES) {
        return image.toComposeImageBitmap()
    }
    // 다운샘플링: 원본 픽셀이 한도를 초과하면 축소 렌더링
    val scale = sqrt(MAX_BITMAP_BYTES.toDouble() / pixelBytes).toFloat()
    val newW = (image.width * scale).toInt().coerceAtLeast(1)
    val newH = (image.height * scale).toInt().coerceAtLeast(1)
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(newW, newH)
    surface.canvas.drawImageRect(
        image,
        org.jetbrains.skia.Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        org.jetbrains.skia.Rect.makeWH(newW.toFloat(), newH.toFloat()),
    )
    return surface.makeImageSnapshot().toComposeImageBitmap()
}

actual suspend fun getImageDimensions(bytes: ByteArray): Pair<Int, Int> =
    withContext(Dispatchers.Default) {
        try {
            // Codec으로 메타데이터만 읽기 (풀 디코딩 제거)
            val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
            codec.width to codec.height
        } catch (_: Exception) {
            // Codec 미지원 포맷 fallback: 풀 디코딩
            val image = Image.makeFromEncoded(bytes)
            image.width to image.height
        }
    }

actual suspend fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray =
    withContext(Dispatchers.Default) {
        val image = Image.makeFromEncoded(bytes)
        val safeX = x.coerceIn(0, image.width - 1)
        val safeY = y.coerceIn(0, image.height - 1)
        val safeW = width.coerceIn(1, image.width - safeX)
        val safeH = height.coerceIn(1, image.height - safeY)

        // Surface.drawImageRect로 필요 영역만 렌더링 (전체 픽셀 복사 제거)
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(safeW, safeH)
        surface.canvas.drawImageRect(
            image,
            org.jetbrains.skia.Rect.makeXYWH(safeX.toFloat(), safeY.toFloat(), safeW.toFloat(), safeH.toFloat()),
            org.jetbrains.skia.Rect.makeWH(safeW.toFloat(), safeH.toFloat()),
        )
        val snapshot = surface.makeImageSnapshot()
        val encoded = snapshot.encodeToData() ?: return@withContext bytes
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
