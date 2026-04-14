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

expect fun getImageDimensions(image: PlatformImage): Pair<Int, Int>
