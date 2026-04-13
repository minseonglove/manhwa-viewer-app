package com.manhwaviewer.data.ocr

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Vision.*
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectGetHeight

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
            val x = (CGRectGetMinX(bbox) * imageWidth).toInt().coerceAtLeast(0)
            val y = ((1.0 - CGRectGetMinY(bbox) - CGRectGetHeight(bbox)) * imageHeight).toInt().coerceAtLeast(0)
            val w = (CGRectGetWidth(bbox) * imageWidth).toInt().coerceAtMost(imageWidth - x)
            val h = (CGRectGetHeight(bbox) * imageHeight).toInt().coerceAtMost(imageHeight - y)
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

        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
        handler.performRequests(listOf(request), error = null)

        return results
    }
}
