package com.manhwaviewer.data.ocr

import kotlinx.cinterop.*
import onnxruntime.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage

class IosPaddleOcrRecognizer : MangaOcrEngine {

    private var api: CPointer<OrtApi>? = null
    private var env: CPointer<OrtEnv>? = null
    private var session: CPointer<OrtSession>? = null
    private lateinit var dictionary: PaddleOcrDictionary
    private var initialized = false

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        if (!initialized) {
            initialize()
            initialized = true
        }

        val (pixelValues, inputW) = decodeAndPreprocessForRec(imageBytes)
        val outputArray = runRecModel(pixelValues, inputW)

        // 출력 shape에서 seqLen, vocabSize 추출
        val vocabSize = outputArray.second
        val seqLen = outputArray.first.size / vocabSize
        val indices = CtcDecoder.greedyDecode(outputArray.first, seqLen, vocabSize)
        return dictionary.decode(indices)
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
                    "paddle_ocr_rec".cstr.ptr,
                    envPtr.ptr,
                )
            )
            env = envPtr.value

            val optionsPtr = alloc<CPointerVar<OrtSessionOptions>>()
            checkStatus(api!!.pointed.CreateSessionOptions!!(optionsPtr.ptr))
            val options = optionsPtr.value!!

            val modelPath = NSBundle.mainBundle.pathForResource("paddleocr/ppocr_v5_rec", "onnx")
                ?: error("PaddleOCR rec model not found in bundle")

            val sessionPtr = alloc<CPointerVar<OrtSession>>()
            checkStatus(
                api!!.pointed.CreateSession!!(env, modelPath.cstr.ptr, options, sessionPtr.ptr)
            )
            session = sessionPtr.value

            api!!.pointed.ReleaseSessionOptions!!(options)
        }

        val dictPath = NSBundle.mainBundle.pathForResource("paddleocr/japan_dict", "txt")
            ?: error("japan_dict.txt not found in bundle")
        val dictContent = NSString.stringWithContentsOfFile(dictPath, NSUTF8StringEncoding, null)
            ?: error("Failed to read japan_dict.txt")
        dictionary = PaddleOcrDictionary(dictContent.toString().lines().filter { it.isNotEmpty() })
    }

    private fun decodeAndPreprocessForRec(imageBytes: ByteArray): Pair<FloatArray, Int> {
        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: error("Failed to decode image")
        val cgImage = uiImage.CGImage ?: error("Failed to get CGImage")

        val imgW = CGImageGetWidth(cgImage).toInt()
        val imgH = CGImageGetHeight(cgImage).toInt()

        val targetH = PaddleOcrConfig.REC_IMAGE_HEIGHT
        val ratio = targetH.toFloat() / imgH
        var targetW = (imgW * ratio).toInt().coerceAtLeast(1)
        if (targetW > PaddleOcrConfig.REC_MAX_WIDTH) targetW = PaddleOcrConfig.REC_MAX_WIDTH

        val bytesPerPixel = 4
        val bytesPerRow = bytesPerPixel * targetW
        val rawData = ByteArray(targetH * targetW * bytesPerPixel)

        rawData.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                targetW.toULong(),
                targetH.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            ) ?: error("Failed to create bitmap context")
            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, targetW.toDouble(), targetH.toDouble()),
                cgImage,
            )
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }

        val area = targetH * targetW
        val result = FloatArray(3 * area)
        for (i in 0 until area) {
            val r = rawData[i * 4].toInt() and 0xFF
            val g = rawData[i * 4 + 1].toInt() and 0xFF
            val b = rawData[i * 4 + 2].toInt() and 0xFF
            result[i] = r / 127.5f - 1.0f
            result[area + i] = g / 127.5f - 1.0f
            result[2 * area + i] = b / 127.5f - 1.0f
        }
        return result to targetW
    }

    private fun runRecModel(pixelValues: FloatArray, inputW: Int): Pair<FloatArray, Int> = memScoped {
        val targetH = PaddleOcrConfig.REC_IMAGE_HEIGHT
        val shape = allocArrayOf(1L, 3L, targetH.toLong(), inputW.toLong())
        val inputTensor = createFloatTensor(pixelValues, shape, 4)

        val inputName = allocArrayOf("x".cstr.ptr)
        val outputName = allocArrayOf("softmax_0.tmp_0".cstr.ptr)
        val outputTensorPtr = alloc<CPointerVar<OrtValue>>()

        // 출력 이름을 모를 수 있으므로, 첫 번째 출력 이름을 동적으로 가져옴
        val allocatorPtr = alloc<CPointerVar<OrtAllocator>>()
        checkStatus(api!!.pointed.GetAllocatorWithDefaultOptions!!(allocatorPtr.ptr))

        val outNamePtr = alloc<CPointerVar<ByteVar>>()
        checkStatus(api!!.pointed.SessionGetOutputName!!(session, 0u, allocatorPtr.value, outNamePtr.ptr))
        val actualOutputName = outNamePtr.value!!.toKString()

        val outputNames = allocArrayOf(actualOutputName.cstr.ptr)

        checkStatus(
            api!!.pointed.Run!!(
                session, null,
                inputName, inputTensor.ptr, 1u,
                outputNames, 1u,
                outputTensorPtr.ptr,
            )
        )

        // 출력 shape 추출
        val infoPtr = alloc<CPointerVar<OrtTensorTypeAndShapeInfo>>()
        checkStatus(api!!.pointed.GetTensorTypeAndShape!!(outputTensorPtr.value, infoPtr.ptr))

        val dimsCountPtr = alloc<ULongVar>()
        checkStatus(api!!.pointed.GetDimensionsCount!!(infoPtr.value, dimsCountPtr.ptr))
        val dimsCount = dimsCountPtr.value.toInt()

        val dims = LongArray(dimsCount)
        dims.usePinned { pinned ->
            checkStatus(api!!.pointed.GetDimensions!!(infoPtr.value, pinned.addressOf(0), dimsCount.toULong()))
        }
        api!!.pointed.ReleaseTensorTypeAndShapeInfo!!(infoPtr.value)

        val vocabSize = dims[2].toInt()
        val output = extractFloatData(outputTensorPtr.value!!)

        api!!.pointed.ReleaseValue!!(inputTensor.value)
        api!!.pointed.ReleaseValue!!(outputTensorPtr.value)

        output to vocabSize
    }

    private fun MemScope.createFloatTensor(
        data: FloatArray,
        shape: CArrayPointer<LongVar>,
        shapeDims: Int,
    ): CPointerVar<OrtValue> {
        val memInfoPtr = alloc<CPointerVar<OrtMemoryInfo>>()
        checkStatus(
            api!!.pointed.CreateCpuMemoryInfo!!(
                OrtArenaAllocator, OrtMemTypeDefault, memInfoPtr.ptr,
            )
        )
        val tensorPtr = alloc<CPointerVar<OrtValue>>()
        data.usePinned { pinned ->
            checkStatus(
                api!!.pointed.CreateTensorWithDataAsOrtValue!!(
                    memInfoPtr.value,
                    pinned.addressOf(0),
                    (data.size * 4).toULong(),
                    shape,
                    shapeDims.toULong(),
                    ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT,
                    tensorPtr.ptr,
                )
            )
        }
        api!!.pointed.ReleaseMemoryInfo!!(memInfoPtr.value)
        return tensorPtr
    }

    private fun extractFloatData(tensor: CPointer<OrtValue>): FloatArray = memScoped {
        val dataPtr = alloc<COpaquePointerVar>()
        checkStatus(api!!.pointed.GetTensorMutableData!!(tensor, dataPtr.ptr))

        val infoPtr = alloc<CPointerVar<OrtTensorTypeAndShapeInfo>>()
        checkStatus(api!!.pointed.GetTensorTypeAndShape!!(tensor, infoPtr.ptr))

        val countPtr = alloc<ULongVar>()
        checkStatus(api!!.pointed.GetTensorShapeElementCount!!(infoPtr.value, countPtr.ptr))
        val count = countPtr.value.toInt()

        api!!.pointed.ReleaseTensorTypeAndShapeInfo!!(infoPtr.value)

        val floatPtr = dataPtr.value!!.reinterpret<FloatVar>()
        FloatArray(count) { floatPtr[it] }
    }

    private fun checkStatus(status: CPointer<OrtStatus>?) {
        if (status != null) {
            val msg = api!!.pointed.GetErrorMessage!!(status)?.toKString() ?: "Unknown ORT error"
            api!!.pointed.ReleaseStatus!!(status)
            error("ONNX Runtime error: $msg")
        }
    }

    override fun close() {
        session?.let { api?.pointed?.ReleaseSession?.invoke(it) }
        env?.let { api?.pointed?.ReleaseEnv?.invoke(it) }
        session = null
        env = null
        api = null
        initialized = false
    }
}
