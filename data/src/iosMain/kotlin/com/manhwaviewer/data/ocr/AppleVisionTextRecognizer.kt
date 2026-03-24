package com.manhwaviewer.data.ocr

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.Vision.*

class AppleVisionTextRecognizer : PlatformTextRecognizer {

    companion object {
        private const val WIDE_RATIO_THRESHOLD = 3.0f
    }

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        val observations = runRecognition(imageBytes) ?: return ""
        return observations.mapNotNull { obs ->
            (obs.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string
        }.joinToString(" ")
    }

    override suspend fun detectHorizontalText(imageBytes: ByteArray): List<RecognizedHorizontalRegion> {
        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: return emptyList()
        val cgImage = uiImage.CGImage ?: return emptyList()
        val imgW = CGImageGetWidth(cgImage).toInt()
        val imgH = CGImageGetHeight(cgImage).toInt()

        val observations = runRecognition(imageBytes) ?: return emptyList()

        return observations.mapNotNull { obs ->
            val bbox = obs.boundingBox
            val x = (bbox.origin.x * imgW).toInt().coerceAtLeast(0)
            val y = ((1.0 - bbox.origin.y - bbox.size.height) * imgH).toInt().coerceAtLeast(0)
            val w = (bbox.size.width * imgW).toInt().coerceAtMost(imgW - x)
            val h = (bbox.size.height * imgH).toInt().coerceAtMost(imgH - y)

            if (h <= 0 || w.toFloat() / h < WIDE_RATIO_THRESHOLD) return@mapNotNull null

            val text = (obs.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string
                ?: return@mapNotNull null

            RecognizedHorizontalRegion(x = x, y = y, width = w, height = h, text = text)
        }
    }

    private fun runRecognition(imageBytes: ByteArray): List<VNRecognizedTextObservation>? {
        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: return null
        val cgImage = uiImage.CGImage ?: return null

        val results = mutableListOf<VNRecognizedTextObservation>()
        val request = VNRecognizeTextRequest { req, error ->
            if (error != null) return@VNRecognizeTextRequest
            val observations = req?.results as? List<*> ?: return@VNRecognizeTextRequest
            observations.filterIsInstance<VNRecognizedTextObservation>().forEach {
                results.add(it)
            }
        }
        request.setRecognitionLanguages(listOf("ja", "en"))
        request.setRecognitionLevel(VNRequestTextRecognitionLevelAccurate)

        val handler = VNImageRequestHandler(cgImage, NSDictionary())
        handler.performRequests(listOf(request), null)

        return results
    }
}
