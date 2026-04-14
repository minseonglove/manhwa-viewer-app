package com.manhwaviewer.data.ocr.image

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

actual typealias PlatformImage = UIImage

actual fun decodeImage(bytes: ByteArray): PlatformImage {
    val nsData = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return UIImage.imageWithData(nsData) ?: throw IllegalArgumentException("Failed to decode image bytes")
}

actual fun resizeAndPadToFloatArray(
    image: PlatformImage,
    targetSize: Int,
    normRange: NormRange,
    padColor: Float,
): FloatArray {
    val cgImage = image.CGImage ?: throw IllegalArgumentException("Failed to get CGImage from UIImage")

    val imgW = CGImageGetWidth(cgImage).toInt()
    val imgH = CGImageGetHeight(cgImage).toInt()
    val scale = targetSize.toDouble() / maxOf(imgW, imgH)
    val scaledW = (imgW * scale).toInt().coerceAtLeast(1)
    val scaledH = (imgH * scale).toInt().coerceAtLeast(1)
    val offsetX = (targetSize - scaledW) / 2
    val offsetY = (targetSize - scaledH) / 2

    val bytesPerPixel = 4
    val bytesPerRow = bytesPerPixel * targetSize
    val rawData = ByteArray(targetSize * targetSize * bytesPerPixel)

    rawData.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            pinned.addressOf(0),
            targetSize.toULong(),
            targetSize.toULong(),
            8u,
            bytesPerRow.toULong(),
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
        ) ?: error("Failed to create bitmap context")

        CGContextSetRGBFillColor(context, padColor.toDouble(), padColor.toDouble(), padColor.toDouble(), 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, targetSize.toDouble(), targetSize.toDouble()))
        CGContextDrawImage(
            context,
            CGRectMake(offsetX.toDouble(), offsetY.toDouble(), scaledW.toDouble(), scaledH.toDouble()),
            cgImage,
        )
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
    }

    val area = targetSize * targetSize
    val result = FloatArray(3 * area)
    for (i in 0 until area) {
        val r = rawData[i * 4].toInt() and 0xFF
        val g = rawData[i * 4 + 1].toInt() and 0xFF
        val b = rawData[i * 4 + 2].toInt() and 0xFF
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
    val cgImage = image.CGImage ?: throw IllegalArgumentException("Failed to get CGImage from UIImage")
    val cropRect = CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())
    val croppedCgImage = CGImageCreateWithImageInRect(cgImage, cropRect)
        ?: throw IllegalArgumentException("Failed to crop CGImage")
    val croppedUiImage = UIImage.imageWithCGImage(croppedCgImage)
    CGImageRelease(croppedCgImage)
    val jpegData = UIImageJPEGRepresentation(croppedUiImage, 0.85)
        ?: throw IllegalArgumentException("Failed to encode cropped image as JPEG")
    val length = jpegData.length.toInt()
    val result = ByteArray(length)
    result.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), jpegData.bytes, length.toULong())
    }
    return result
}

actual fun computeBorderAverageColor(image: PlatformImage): Float {
    val cgImage = image.CGImage ?: return 1.0f
    val origW = CGImageGetWidth(cgImage).toInt()
    val origH = CGImageGetHeight(cgImage).toInt()
    if (origW == 0 || origH == 0) return 1.0f

    // Downsample to max 200px to avoid decoding the full image
    val maxDim = maxOf(origW, origH)
    val sampleScale = if (maxDim > 200) 200.0 / maxDim else 1.0
    val w = (origW * sampleScale).toInt().coerceAtLeast(1)
    val h = (origH * sampleScale).toInt().coerceAtLeast(1)

    val bytesPerPixel = 4
    val bytesPerRow = bytesPerPixel * w
    val rawData = ByteArray(w * h * bytesPerPixel)

    rawData.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            pinned.addressOf(0),
            w.toULong(),
            h.toULong(),
            8u,
            bytesPerRow.toULong(),
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
        ) ?: run {
            CGColorSpaceRelease(colorSpace)
            return 1.0f
        }
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cgImage)
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
    }

    var sum = 0L
    var count = 0
    val xStep = maxOf(1, w / 50)
    val yStep = maxOf(1, h / 50)

    // Top and bottom rows
    for (y in intArrayOf(0, h - 1)) {
        var x = 0
        while (x < w) {
            val offset = (y * w + x) * bytesPerPixel
            val r = rawData[offset].toInt() and 0xFF
            val g = rawData[offset + 1].toInt() and 0xFF
            val b = rawData[offset + 2].toInt() and 0xFF
            sum += (r + g + b) / 3
            count++
            x += xStep
        }
    }
    // Left and right columns
    for (x in intArrayOf(0, w - 1)) {
        var y = 0
        while (y < h) {
            val offset = (y * w + x) * bytesPerPixel
            val r = rawData[offset].toInt() and 0xFF
            val g = rawData[offset + 1].toInt() and 0xFF
            val b = rawData[offset + 2].toInt() and 0xFF
            sum += (r + g + b) / 3
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
): PlatformImage {
    val cgImage = image.CGImage ?: throw IllegalArgumentException("Failed to get CGImage from UIImage")
    val cropRect = CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())
    val croppedCgImage = CGImageCreateWithImageInRect(cgImage, cropRect)
        ?: throw IllegalArgumentException("Failed to crop CGImage")
    val result = UIImage.imageWithCGImage(croppedCgImage)
    CGImageRelease(croppedCgImage)
    return result
}

actual fun getImageDimensions(image: PlatformImage): Pair<Int, Int> {
    val cgImage = image.CGImage ?: throw IllegalArgumentException("Failed to get CGImage from UIImage")
    return CGImageGetWidth(cgImage).toInt() to CGImageGetHeight(cgImage).toInt()
}
