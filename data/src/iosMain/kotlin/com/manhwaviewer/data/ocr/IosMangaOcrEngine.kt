package com.manhwaviewer.data.ocr

import kotlinx.cinterop.*
import onnxruntime.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage

class IosMangaOcrEngine : MangaOcrEngine {

    private var api: CPointer<OrtApi>? = null
    private var env: CPointer<OrtEnv>? = null
    private var encoderSession: CPointer<OrtSession>? = null
    private var decoderSession: CPointer<OrtSession>? = null
    private lateinit var tokenizer: MangaOcrTokenizer
    private var initialized = false

    override suspend fun recognizeText(imageBytes: ByteArray): String {
        if (!initialized) {
            initialize()
            initialized = true
        }

        val pixelValues = decodeAndPreprocessImage(imageBytes)
        val encoderOutput = runEncoder(pixelValues)
        val tokenIds = greedyDecode(encoderOutput)
        return tokenizer.decode(tokenIds)
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
                    "manga_ocr".cstr.ptr,
                    envPtr.ptr,
                )
            )
            env = envPtr.value

            val optionsPtr = alloc<CPointerVar<OrtSessionOptions>>()
            checkStatus(api!!.pointed.CreateSessionOptions!!(optionsPtr.ptr))
            val options = optionsPtr.value!!

            val bundle = NSBundle.mainBundle
            val encoderPath = bundle.pathForResource("mangaocr/encoder_model", "onnx")
                ?: error("Encoder model not found in bundle")
            val decoderPath = bundle.pathForResource("mangaocr/decoder_model", "onnx")
                ?: error("Decoder model not found in bundle")

            val encSessionPtr = alloc<CPointerVar<OrtSession>>()
            checkStatus(
                api!!.pointed.CreateSession!!(env, encoderPath.cstr.ptr, options, encSessionPtr.ptr)
            )
            encoderSession = encSessionPtr.value

            val decSessionPtr = alloc<CPointerVar<OrtSession>>()
            checkStatus(
                api!!.pointed.CreateSession!!(env, decoderPath.cstr.ptr, options, decSessionPtr.ptr)
            )
            decoderSession = decSessionPtr.value

            api!!.pointed.ReleaseSessionOptions!!(options)
        }

        val vocabPath = NSBundle.mainBundle.pathForResource("mangaocr/vocab", "txt")
            ?: error("Vocab file not found in bundle")
        val vocabContent = NSString.stringWithContentsOfFile(vocabPath, NSUTF8StringEncoding, null)
            ?: error("Failed to read vocab file")
        tokenizer = MangaOcrTokenizer(vocabContent.toString().lines())
    }

    private fun decodeAndPreprocessImage(imageBytes: ByteArray): FloatArray {
        val nsData = imageBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: error("Failed to decode image")
        val cgImage = uiImage.CGImage ?: error("Failed to get CGImage")

        val size = MangaOcrConfig.IMAGE_SIZE
        val imgW = CGImageGetWidth(cgImage).toInt()
        val imgH = CGImageGetHeight(cgImage).toInt()
        val scale = size.toDouble() / maxOf(imgW, imgH)
        val scaledW = (imgW * scale).toInt().coerceAtLeast(1)
        val scaledH = (imgH * scale).toInt().coerceAtLeast(1)
        val offsetX = (size - scaledW) / 2
        val offsetY = (size - scaledH) / 2

        val bytesPerPixel = 4
        val bytesPerRow = bytesPerPixel * size
        val rawData = ByteArray(size * size * bytesPerPixel)

        rawData.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                size.toULong(),
                size.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or
                    kCGBitmapByteOrder32Big,
            ) ?: error("Failed to create bitmap context")
            // 흰색 배경으로 채운 뒤, 비율 유지 리사이즈 이미지를 중앙에 배치
            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, size.toDouble(), size.toDouble()))
            CGContextDrawImage(
                context,
                CGRectMake(offsetX.toDouble(), offsetY.toDouble(), scaledW.toDouble(), scaledH.toDouble()),
                cgImage,
            )
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }

        val result = FloatArray(3 * size * size)
        for (i in 0 until size * size) {
            val r = rawData[i * 4].toInt() and 0xFF
            val g = rawData[i * 4 + 1].toInt() and 0xFF
            val b = rawData[i * 4 + 2].toInt() and 0xFF
            result[i] = r / 127.5f - 1.0f
            result[size * size + i] = g / 127.5f - 1.0f
            result[2 * size * size + i] = b / 127.5f - 1.0f
        }
        return result
    }

    private fun runEncoder(pixelValues: FloatArray): FloatArray = memScoped {
        val size = MangaOcrConfig.IMAGE_SIZE
        val shape = allocArrayOf(1L, 3L, size.toLong(), size.toLong())
        val inputTensor = createFloatTensor(pixelValues, shape, 4)

        val inputName = allocArrayOf("pixel_values".cstr.ptr)
        val outputName = allocArrayOf("last_hidden_state".cstr.ptr)
        val outputTensorPtr = alloc<CPointerVar<OrtValue>>()

        checkStatus(
            api!!.pointed.Run!!(
                encoderSession, null,
                inputName, inputTensor.ptr, 1u,
                outputName, 1u,
                outputTensorPtr.ptr,
            )
        )

        val output = extractFloatData(outputTensorPtr.value!!)
        api!!.pointed.ReleaseValue!!(inputTensor.value)
        api!!.pointed.ReleaseValue!!(outputTensorPtr.value)
        output
    }

    private fun greedyDecode(encoderOutput: FloatArray): List<Int> = memScoped {
        val encoderShape = allocArrayOf(
            1L,
            MangaOcrConfig.ENCODER_SEQ_LENGTH.toLong(),
            MangaOcrConfig.ENCODER_HIDDEN_SIZE.toLong(),
        )
        val encoderTensor = createFloatTensor(encoderOutput, encoderShape, 3)

        val inputIds = LongArray(MangaOcrConfig.MAX_LENGTH + 1)
        inputIds[0] = MangaOcrConfig.DECODER_START_TOKEN_ID.toLong()
        var seqLen = 1

        try {
            for (step in 0 until MangaOcrConfig.MAX_LENGTH) {
                val maxIdx = runDecoderStep(inputIds, seqLen, encoderTensor.value!!)
                if (maxIdx == MangaOcrConfig.EOS_TOKEN_ID) break
                inputIds[seqLen++] = maxIdx.toLong()
            }
        } finally {
            api!!.pointed.ReleaseValue!!(encoderTensor.value)
        }

        (0 until seqLen).map { inputIds[it].toInt() }
    }

    private fun runDecoderStep(
        inputIds: LongArray,
        seqLen: Int,
        encoderOrtValue: CPointer<OrtValue>,
    ): Int = memScoped {
        val inputIdsShape = allocArrayOf(1L, seqLen.toLong())
        val inputIdsTensor = createLongTensorSlice(inputIds, seqLen, inputIdsShape, 2)

        val inputNames = allocArrayOf(
            "input_ids".cstr.ptr,
            "encoder_hidden_states".cstr.ptr,
        )
        val inputTensors = allocArrayOf(inputIdsTensor.value!!, encoderOrtValue)
        val outputName = allocArrayOf("logits".cstr.ptr)
        val outputTensorPtr = alloc<CPointerVar<OrtValue>>()

        checkStatus(
            api!!.pointed.Run!!(
                decoderSession, null,
                inputNames, inputTensors, 2u,
                outputName, 1u,
                outputTensorPtr.ptr,
            )
        )

        val dataPtr = alloc<COpaquePointerVar>()
        checkStatus(api!!.pointed.GetTensorMutableData!!(outputTensorPtr.value, dataPtr.ptr))
        val floatPtr = dataPtr.value!!.reinterpret<FloatVar>()
        val offset = (seqLen - 1) * MangaOcrConfig.VOCAB_SIZE

        var maxIdx = 0
        var maxVal = floatPtr[offset]
        for (i in 1 until MangaOcrConfig.VOCAB_SIZE) {
            val v = floatPtr[offset + i]
            if (v > maxVal) {
                maxVal = v
                maxIdx = i
            }
        }

        api!!.pointed.ReleaseValue!!(inputIdsTensor.value)
        api!!.pointed.ReleaseValue!!(outputTensorPtr.value)
        maxIdx
    }

    private fun MemScope.createFloatTensor(
        data: FloatArray,
        shape: CArrayPointer<LongVar>,
        shapeDims: Int,
    ): CPointerVar<OrtValue> {
        val memInfoPtr = alloc<CPointerVar<OrtMemoryInfo>>()
        checkStatus(
            api!!.pointed.CreateCpuMemoryInfo!!(
                OrtArenaAllocator, OrtMemTypeDefault, memInfoPtr.ptr
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

    private fun MemScope.createLongTensorSlice(
        data: LongArray,
        length: Int,
        shape: CArrayPointer<LongVar>,
        shapeDims: Int,
    ): CPointerVar<OrtValue> {
        val memInfoPtr = alloc<CPointerVar<OrtMemoryInfo>>()
        checkStatus(
            api!!.pointed.CreateCpuMemoryInfo!!(
                OrtArenaAllocator, OrtMemTypeDefault, memInfoPtr.ptr
            )
        )
        val tensorPtr = alloc<CPointerVar<OrtValue>>()
        data.usePinned { pinned ->
            checkStatus(
                api!!.pointed.CreateTensorWithDataAsOrtValue!!(
                    memInfoPtr.value,
                    pinned.addressOf(0),
                    (length * 8).toULong(),
                    shape,
                    shapeDims.toULong(),
                    ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
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
        encoderSession?.let { api?.pointed?.ReleaseSession?.invoke(it) }
        decoderSession?.let { api?.pointed?.ReleaseSession?.invoke(it) }
        env?.let { api?.pointed?.ReleaseEnv?.invoke(it) }
        encoderSession = null
        decoderSession = null
        env = null
        api = null
        initialized = false
    }
}
