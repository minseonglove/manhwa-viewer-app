package com.manhwaviewer.data.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrRoutingTest {

    private val wide = RegionBox(x = 0, y = 0, width = 200, height = 50)
    private val tall = RegionBox(x = 300, y = 0, width = 50, height = 200)
    private val ambiguous = RegionBox(x = 0, y = 300, width = 160, height = 100)
    private val smallSquare = RegionBox(x = 400, y = 400, width = 32, height = 30)

    @Test
    fun leavesWideRegionsToThePlatformRecognizer() {
        val routing = routeRegions(listOf(wide, tall), platformBlocks = emptyList(), platformAvailable = true)

        assertEquals(listOf(1), routing.ocrTargets)
        assertEquals(1, routing.skippedHorizontal)
    }

    @Test
    fun sendsWideRegionsToTheOcrModelWhenNothingElseWouldReadThem() {
        val routing = routeRegions(listOf(wide, tall), platformBlocks = emptyList(), platformAvailable = false)

        assertEquals(listOf(0, 1), routing.ocrTargets)
        assertEquals(0, routing.skippedHorizontal)
    }

    @Test
    fun ambiguousRegionGoesToTheOcrModelOnlyWhenThePlatformDidNotReadIt() {
        val overlapping = RegionBox(x = 5, y = 305, width = 150, height = 90)
        val elsewhere = RegionBox(x = 900, y = 900, width = 100, height = 40)

        assertEquals(emptyList(), routeRegions(listOf(ambiguous), listOf(overlapping), platformAvailable = true).ocrTargets)
        assertEquals(listOf(0), routeRegions(listOf(ambiguous), listOf(elsewhere), platformAvailable = true).ocrTargets)
    }

    @Test
    fun smallNearSquareRegionIsTreatedAsAmbiguous() {
        val overlapping = RegionBox(x = 400, y = 400, width = 32, height = 30)

        assertEquals(emptyList(), routeRegions(listOf(smallSquare), listOf(overlapping), platformAvailable = true).ocrTargets)
        assertEquals(listOf(0), routeRegions(listOf(smallSquare), emptyList(), platformAvailable = true).ocrTargets)
    }

    @Test
    fun dropsPlatformBlocksThatOverlapAVerticalRegion() {
        val onTheTallRegion = RegionBox(x = 302, y = 5, width = 46, height = 190)
        val elsewhere = RegionBox(x = 900, y = 900, width = 100, height = 40)

        val routing = routeRegions(listOf(tall), listOf(onTheTallRegion, elsewhere), platformAvailable = true)

        assertEquals(listOf(1), routing.keptPlatformBlocks)
    }

    @Test
    fun keepsNoPlatformBlocksWhenThePlatformIsNotUsed() {
        val routing = routeRegions(listOf(tall), listOf(RegionBox(900, 900, 100, 40)), platformAvailable = false)

        assertEquals(emptyList(), routing.keptPlatformBlocks)
    }
}
