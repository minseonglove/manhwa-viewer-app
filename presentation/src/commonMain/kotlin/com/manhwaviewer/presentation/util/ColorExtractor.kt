package com.manhwaviewer.presentation.util

import androidx.compose.ui.graphics.Color
import com.manhwaviewer.domain.entity.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun extractDominantColor(
    pixels: IntArray,
    imageWidth: Int,
    blockX: Int,
    blockY: Int,
    blockWidth: Int,
    blockHeight: Int,
): Color {
    if (blockWidth <= 0 || blockHeight <= 0) return Color.Gray

    val safeX = blockX.coerceAtLeast(0)
    val safeY = blockY.coerceAtLeast(0)
    val safeRight = (blockX + blockWidth).coerceAtMost(imageWidth)
    val safeW = safeRight - safeX
    if (safeW <= 0) return Color.Gray

    val imageHeight = pixels.size / imageWidth
    val safeBottom = (blockY + blockHeight).coerceAtMost(imageHeight)
    val safeH = safeBottom - safeY
    if (safeH <= 0) return Color.Gray

    // 5비트 양자화 — flat IntArray (32768 버킷 × 4채널)
    val bucketSize = 32768
    val data = IntArray(bucketSize * 4) // [count, rSum, gSum, bSum] per bucket
    val stride = when {
        safeW * safeH > 10000 -> 4
        safeW * safeH > 2500 -> 3
        else -> 2
    }

    for (dy in 0 until safeH step stride) {
        val rowOffset = (safeY + dy) * imageWidth
        for (dx in 0 until safeW step stride) {
            val pixel = pixels[rowOffset + safeX + dx]
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

    // 최대 빈도 버킷 탐색
    var bestCount = 0
    var bestIdx = 0
    for (i in 0 until bucketSize) {
        val count = data[i * 4]
        if (count > bestCount) {
            bestCount = count
            bestIdx = i
        }
    }

    if (bestCount == 0) return Color.Gray

    val base = bestIdx * 4
    return Color(
        red = (data[base + 1] / bestCount) / 255f,
        green = (data[base + 2] / bestCount) / 255f,
        blue = (data[base + 3] / bestCount) / 255f,
    )
}

suspend fun extractDominantColors(
    imageBytes: ByteArray,
    textBlocks: List<TextBlock>,
): List<Color> = withContext(Dispatchers.Default) {
    if (textBlocks.isEmpty()) return@withContext emptyList()
    val imageBitmap = imageBytes.toImageBitmap()
    val width = imageBitmap.width
    val height = imageBitmap.height
    val pixels = IntArray(width * height)
    imageBitmap.readPixels(pixels)
    textBlocks.map { block ->
        extractDominantColor(pixels, width, block.x, block.y, block.width, block.height)
    }
}

fun contrastingTextColor(bg: Color): Color {
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}
