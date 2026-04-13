package com.manhwaviewer.data.ocr

import kotlinx.cinterop.*
import onnxruntime.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

class IosYoloTextDetector : TextDetector {

    companion object {
        private const val CONF_THRESHOLD = 0.3f
        private const val TEXT_CLASS_ID = 1f
        private const val PADDING_RATIO = 0.08f
        private const val MIN_PADDING_PX = 4
        private const val MAX_PADDING_PX = 20
    }

    private var api: CPointer<OrtApi>? = null
    private var env: CPointer<OrtEnv>? = null
    private var session: CPointer<OrtSession>? = null
    private var initialized = false

    override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
        if (!initialized) {
            initialize()
            initialized = true
        }

        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: return emptyList()
        val cgImage = uiImage.CGImage ?: return emptyList()

        val imageWidth = CGImageGetWidth(cgImage).toInt()
        val imageHeight = CGImageGetHeight(cgImage).toInt()

        val inputSize = MangaOcrConfig.YOLO_INPUT_SIZE
        val (pixelData, transform) = preprocessImage(cgImage, imageWidth, imageHeight, inputSize)
        val outputData = runInference(pixelData, inputSize)
        val boxes = postProcess(outputData, transform, imageWidth, imageHeight)

        return boxes.mapNotNull { box ->
            val pad = (maxOf(box.width, box.height) * PADDING_RATIO)
                .toInt().coerceIn(MIN_PADDING_PX, MAX_PADDING_PX)
            val x = (box.x - pad).coerceAtLeast(0)
            val y = (box.y - pad).coerceAtLeast(0)
            val w = (box.width + pad * 2).coerceAtMost(imageWidth - x)
            val h = (box.height + pad * 2).coerceAtMost(imageHeight - y)
            if (w <= 0 || h <= 0) return@mapNotNull null

            val croppedCgImage = CGImageCreateWithImageInRect(
                cgImage, CGRectMake(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())
            ) ?: return@mapNotNull null

            val croppedUiImage = UIImage.imageWithCGImage(croppedCgImage)
            val jpegData = UIImageJPEGRepresentation(croppedUiImage, 0.85)
                ?: return@mapNotNull null
            val bytes = jpegData.bytes?.readBytes(jpegData.length.toInt())
                ?: return@mapNotNull null

            DetectedRegion(x = x, y = y, width = w, height = h, croppedImageBytes = bytes)
        }
    }

    private fun initialize() {
        val apiBase = OrtGetApiBase() ?: error("Failed to get ORT API base")
        api = apiBase.pointed.GetApi!!(ORT_API_VERSION.toUInt())
            ?: error("Failed to get ORT API")

        memScoped {
            val envPtr = alloc<CPointerVar<OrtEnv>>()
            checkStatus(
                api!!.pointed.CreateEnv!!(
                    ORT_LOGGING_LEVEL_WARNING,
                    "yolo_detector".cstr.ptr,
                    envPtr.ptr,
                )
            )
            env = envPtr.value

            val optionsPtr = alloc<CPointerVar<OrtSessionOptions>>()
            checkStatus(api!!.pointed.CreateSessionOptions!!(optionsPtr.ptr))
            val options = optionsPtr.value!!

            val modelPath = NSBundle.mainBundle.pathForResource(
                "mangaocr/manga_panel_detector_fp16", "onnx"
            ) ?: error("YOLO model not found in bundle")

            val sessionPtr = alloc<CPointerVar<OrtSession>>()
            checkStatus(
                api!!.pointed.CreateSession!!(env, modelPath.cstr.ptr, options, sessionPtr.ptr)
            )
            session = sessionPtr.value

            api!!.pointed.ReleaseSessionOptions!!(options)
        }
    }

    private fun preprocessImage(
        cgImage: CGImageRef,
        imageWidth: Int,
        imageHeight: Int,
        inputSize: Int,
    ): Pair<FloatArray, YoloTransformIos> {
        val scale = inputSize.toFloat() / maxOf(imageWidth, imageHeight)
        val scaledW = (imageWidth * scale).toInt().coerceAtLeast(1)
        val scaledH = (imageHeight * scale).toInt().coerceAtLeast(1)
        val padX = (inputSize - scaledW) / 2
        val padY = (inputSize - scaledH) / 2

        val bytesPerPixel = 4
        val bytesPerRow = bytesPerPixel * inputSize
        val rawData = ByteArray(inputSize * inputSize * bytesPerPixel)

        rawData.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                inputSize.toULong(),
                inputSize.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            ) ?: error("Failed to create bitmap context")

            // Fill with gray (128/255 ≈ 0.5)
            CGContextSetRGBFillColor(context, 0.5, 0.5, 0.5, 1.0)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, inputSize.toDouble(), inputSize.toDouble()))
            CGContextDrawImage(
                context,
                CGRectMake(padX.toDouble(), padY.toDouble(), scaledW.toDouble(), scaledH.toDouble()),
                cgImage,
            )
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }

        val area = inputSize * inputSize
        val result = FloatArray(3 * area)
        for (i in 0 until area) {
            val r = rawData[i * 4].toInt() and 0xFF
            val g = rawData[i * 4 + 1].toInt() and 0xFF
            val b = rawData[i * 4 + 2].toInt() and 0xFF
            result[i] = r / 255.0f
            result[area + i] = g / 255.0f
            result[2 * area + i] = b / 255.0f
        }

        return result to YoloTransformIos(scale, padX, padY)
    }

    private fun runInference(pixelData: FloatArray, inputSize: Int): FloatArray = memScoped {
        val shape = allocArrayOf(1L, 3L, inputSize.toLong(), inputSize.toLong())

        val memInfoPtr = alloc<CPointerVar<OrtMemoryInfo>>()
        checkStatus(
            api!!.pointed.CreateCpuMemoryInfo!!(
                OrtArenaAllocator, OrtMemTypeDefault, memInfoPtr.ptr
            )
        )

        val tensorPtr = alloc<CPointerVar<OrtValue>>()
        pixelData.usePinned { pinned ->
            checkStatus(
                api!!.pointed.CreateTensorWithDataAsOrtValue!!(
                    memInfoPtr.value,
                    pinned.addressOf(0),
                    (pixelData.size * 4).toULong(),
                    shape,
                    4u,
                    ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT,
                    tensorPtr.ptr,
                )
            )
        }
        api!!.pointed.ReleaseMemoryInfo!!(memInfoPtr.value)

        val inputName = allocArrayOf("images".cstr.ptr)
        val outputName = allocArrayOf("output0".cstr.ptr)
        val outputTensorPtr = alloc<CPointerVar<OrtValue>>()

        checkStatus(
            api!!.pointed.Run!!(
                session, null,
                inputName, tensorPtr.ptr, 1u,
                outputName, 1u,
                outputTensorPtr.ptr,
            )
        )

        val dataPtr = alloc<COpaquePointerVar>()
        checkStatus(api!!.pointed.GetTensorMutableData!!(outputTensorPtr.value, dataPtr.ptr))
        val floatPtr = dataPtr.value!!.reinterpret<FloatVar>()

        val totalElements = MangaOcrConfig.YOLO_MAX_DETECTIONS * 6
        val output = FloatArray(totalElements) { floatPtr[it] }

        api!!.pointed.ReleaseValue!!(tensorPtr.value)
        api!!.pointed.ReleaseValue!!(outputTensorPtr.value)
        output
    }

    private fun postProcess(
        output: FloatArray,
        transform: YoloTransformIos,
        originalWidth: Int,
        originalHeight: Int,
    ): List<DetectedBox> {
        val boxes = mutableListOf<DetectedBox>()

        for (i in 0 until MangaOcrConfig.YOLO_MAX_DETECTIONS) {
            val offset = i * 6
            val x1 = output[offset]
            val y1 = output[offset + 1]
            val x2 = output[offset + 2]
            val y2 = output[offset + 3]
            val confidence = output[offset + 4]
            val classId = output[offset + 5]

            if (confidence < CONF_THRESHOLD) continue
            if (classId != TEXT_CLASS_ID) continue

            val bx = ((x1 - transform.padX) / transform.scale).toInt().coerceIn(0, originalWidth)
            val by = ((y1 - transform.padY) / transform.scale).toInt().coerceIn(0, originalHeight)
            val bx2 = ((x2 - transform.padX) / transform.scale).toInt().coerceIn(0, originalWidth)
            val by2 = ((y2 - transform.padY) / transform.scale).toInt().coerceIn(0, originalHeight)
            val bw = bx2 - bx
            val bh = by2 - by
            if (bw <= 0 || bh <= 0) continue

            boxes.add(DetectedBox(bx, by, bw, bh, confidence, isJapanese = true))
        }

        return boxes
    }

    private fun checkStatus(status: CPointer<OrtStatus>?) {
        if (status != null) {
            val msg = api!!.pointed.GetErrorMessage!!(status)?.toKString() ?: "Unknown ORT error"
            api!!.pointed.ReleaseStatus!!(status)
            error("ONNX Runtime error: $msg")
        }
    }
}

private data class YoloTransformIos(
    val scale: Float,
    val padX: Int,
    val padY: Int,
)
