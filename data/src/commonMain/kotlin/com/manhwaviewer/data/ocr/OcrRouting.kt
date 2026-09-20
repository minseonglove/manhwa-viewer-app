package com.manhwaviewer.data.ocr

internal data class RegionBox(val x: Int, val y: Int, val width: Int, val height: Int)

/** Indices into the lists handed to [routeRegions]. */
internal class OcrRouting(
    val ocrTargets: List<Int>,
    val keptPlatformBlocks: List<Int>,
    val skippedHorizontal: Int,
    val verticalCount: Int,
    val ambiguousCount: Int,
)

private const val HORIZONTAL_RATIO = 2.0f
private const val AMBIGUOUS_RATIO = 1.5f
private const val OVERLAP_IOU_THRESHOLD = 0.3f
private const val SMALL_REGION_MIN_DIM = 40
private const val NEAR_SQUARE_LOW = 0.8f

/**
 * Decides who reads each detected region.
 *
 * The OCR model is trained on vertical manga text, so wide regions are left to the platform
 * recognizer and the in-between ones go to whichever of the two actually produced something there.
 * Without a platform recognizer nothing else would read the wide regions, so the OCR model gets
 * every region rather than silently dropping them.
 */
internal fun routeRegions(
    regions: List<RegionBox>,
    platformBlocks: List<RegionBox>,
    platformAvailable: Boolean,
): OcrRouting {
    if (!platformAvailable) {
        return OcrRouting(
            ocrTargets = regions.indices.toList(),
            keptPlatformBlocks = emptyList(),
            skippedHorizontal = 0,
            verticalCount = regions.size,
            ambiguousCount = 0,
        )
    }

    val horizontal = mutableListOf<Int>()
    val ambiguous = mutableListOf<Int>()
    val vertical = mutableListOf<Int>()
    for ((index, region) in regions.withIndex()) {
        val ratio = if (region.height > 0) region.width.toFloat() / region.height else 0f
        val small = minOf(region.width, region.height) < SMALL_REGION_MIN_DIM
        when {
            region.height > 0 && ratio >= HORIZONTAL_RATIO -> horizontal += index
            region.height > 0 && (ratio >= AMBIGUOUS_RATIO || (small && ratio >= NEAR_SQUARE_LOW)) -> ambiguous += index
            else -> vertical += index
        }
    }

    val keptPlatformBlocks = platformBlocks.indices.filter { block ->
        vertical.none { iou(regions[it], platformBlocks[block]) > OVERLAP_IOU_THRESHOLD }
    }
    val ambiguousTargets = ambiguous.filter { region ->
        platformBlocks.none { iou(regions[region], it) > OVERLAP_IOU_THRESHOLD }
    }

    return OcrRouting(
        ocrTargets = (vertical + ambiguousTargets).sorted(),
        keptPlatformBlocks = keptPlatformBlocks,
        skippedHorizontal = horizontal.size,
        verticalCount = vertical.size,
        ambiguousCount = ambiguous.size,
    )
}

private fun iou(a: RegionBox, b: RegionBox): Float {
    val ix1 = maxOf(a.x, b.x)
    val iy1 = maxOf(a.y, b.y)
    val ix2 = minOf(a.x + a.width, b.x + b.width)
    val iy2 = minOf(a.y + a.height, b.y + b.height)
    val intersection = maxOf(0, ix2 - ix1) * maxOf(0, iy2 - iy1)
    if (intersection == 0) return 0f
    val union = a.width * a.height + b.width * b.height - intersection
    return intersection.toFloat() / union.toFloat()
}
