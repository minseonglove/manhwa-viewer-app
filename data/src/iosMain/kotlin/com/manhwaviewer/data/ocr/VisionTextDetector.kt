package com.manhwaviewer.data.ocr

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Vision.*

class VisionTextDetector : TextDetector {

    override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: return emptyList()
        val cgImage = uiImage.CGImage ?: return emptyList()

        val imageWidth = CGImageGetWidth(cgImage).toInt()
        val imageHeight = CGImageGetHeight(cgImage).toInt()

        val observations = detectTextRegions(cgImage)

        return observations.mapNotNull { observation ->
            val bbox = observation.boundingBox
            // Vision bbox는 좌하단 원점 + 정규화 좌표 → 좌상단 원점 픽셀 좌표로 변환
            val x = (bbox.origin.x * imageWidth).toInt().coerceAtLeast(0)
            val y = ((1.0 - bbox.origin.y - bbox.size.height) * imageHeight).toInt().coerceAtLeast(0)
            val w = (bbox.size.width * imageWidth).toInt().coerceAtMost(imageWidth - x)
            val h = (bbox.size.height * imageHeight).toInt().coerceAtMost(imageHeight - y)
            if (w <= 0 || h <= 0) return@mapNotNull null

            val croppedCgImage = CGImageCreateWithImageInRect(
                cgImage, CGRectMake(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())
            ) ?: return@mapNotNull null

            val croppedUiImage = UIImage.imageWithCGImage(croppedCgImage)
            val jpegData = UIImageJPEGRepresentation(croppedUiImage, 0.95)
                ?: return@mapNotNull null
            val bytes = jpegData.bytes?.readBytes(jpegData.length.toInt())
                ?: return@mapNotNull null

            DetectedRegion(x = x, y = y, width = w, height = h, croppedImageBytes = bytes)
        }
    }

    private fun detectTextRegions(cgImage: CGImageRef): List<VNRecognizedTextObservation> {
        val results = mutableListOf<VNRecognizedTextObservation>()

        val request = VNRecognizeTextRequest { request, error ->
            if (error != null) return@VNRecognizeTextRequest
            val observations = request?.results as? List<*> ?: return@VNRecognizeTextRequest
            observations.filterIsInstance<VNRecognizedTextObservation>().forEach {
                results.add(it)
            }
        }
        request.setRecognitionLanguages(listOf("ja"))
        request.setRecognitionLevel(VNRequestTextRecognitionLevelAccurate)

        val handler = VNImageRequestHandler(cgImage, NSDictionary())
        handler.performRequests(listOf(request), null)

        return results
    }
}
