package com.manhwaviewer.data.ocr.image

enum class NormRange {
    /** [0, 1]: pixel / 255 */
    ZERO_ONE,
    /** [-1, 1]: pixel / 127.5 - 1 */
    MINUS_ONE_ONE,
}

expect class PlatformImage

expect fun decodeImage(bytes: ByteArray): PlatformImage

expect fun resizeAndPadToFloatArray(
    image: PlatformImage,
    targetSize: Int,
    normRange: NormRange = NormRange.ZERO_ONE,
    padColor: Float = 1.0f,
): FloatArray

expect fun cropRegion(
    image: PlatformImage,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): ByteArray

expect fun computeBorderAverageColor(image: PlatformImage): Float

expect fun cropToPlatformImage(
    image: PlatformImage,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): PlatformImage

expect fun getImageDimensions(image: PlatformImage): Pair<Int, Int>

/**
 * The image's pixels as HWC uint8 RGB — row-major, three values per pixel, 0..255.
 *
 * This is the layout `HayaiHost.resizeRgb` and the reference implementation both work in.
 *
 * A non-opaque source does not give the same answer on both platforms. Android reads the
 * stored colour and ignores alpha, which is what the reference's `Image.convert("RGB")`
 * does; iOS goes through Core Graphics, which can only composite, so transparent areas
 * come out as the white it composites onto. Scanning accepts png/webp/gif, so this is
 * reachable — it is bounded to "transparent reads as page white on iOS" rather than left
 * as whatever the buffer held.
 */
expect fun readRgbHwc(image: PlatformImage): IntArray
