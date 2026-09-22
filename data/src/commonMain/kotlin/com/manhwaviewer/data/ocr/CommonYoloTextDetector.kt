package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.NormRange
import com.manhwaviewer.data.ocr.image.cropToPlatformImage
import com.manhwaviewer.data.ocr.image.decodeImage
import com.manhwaviewer.data.ocr.image.getImageDimensions
import com.manhwaviewer.data.ocr.image.resizeAndPadToFloatArray
import com.manhwaviewer.data.ocr.inference.YoloRunner

class CommonYoloTextDetector private constructor(
    private val prepare: () -> Unit,
    private val infer: (FloatArray) -> FloatArray,
    private val confThreshold: Float,
) : TextDetector {

    constructor(yolo: YoloRunner, confThreshold: Float = YoloPostProcessor.CONF_THRESHOLD) :
        this(yolo::initialize, yolo::run, confThreshold)

    /**
     * The network behind [infer] instead of a [YoloRunner], for measurements that run it on another
     * runtime; everything around the network call stays the app's.
     */
    constructor(infer: (FloatArray) -> FloatArray, confThreshold: Float = YoloPostProcessor.CONF_THRESHOLD) :
        this({}, infer, confThreshold)

    private var initialized = false

    override suspend fun detect(imageBytes: ByteArray): List<DetectedRegion> {
        if (!initialized) {
            prepare()
            initialized = true
        }

        val image = decodeImage(imageBytes)
        val (origW, origH) = getImageDimensions(image)

        val transform = YoloPostProcessor.computeTransform(origW, origH, MangaOcrConfig.YOLO_INPUT_SIZE)

        val inputData = resizeAndPadToFloatArray(
            image,
            targetSize = MangaOcrConfig.YOLO_INPUT_SIZE,
            normRange = NormRange.ZERO_ONE,
            padColor = 0.5f,
        )

        val rawOutput = infer(inputData)

        val rawBoxes = YoloPostProcessor.parseAndFilter(
            rawOutput = rawOutput,
            scale = transform.scale,
            padX = transform.padX,
            padY = transform.padY,
            originalWidth = origW,
            originalHeight = origH,
            confThreshold = confThreshold,
        )
        val boxes = YoloPostProcessor.nonMaxSuppression(rawBoxes)

        return boxes.mapNotNull { box ->
            val padded = YoloPostProcessor.addPadding(box, origW, origH)
            if (padded.width <= 0 || padded.height <= 0) return@mapNotNull null
            DetectedRegion(
                x = padded.x,
                y = padded.y,
                width = padded.width,
                height = padded.height,
                croppedImage = cropToPlatformImage(image, padded.x, padded.y, padded.width, padded.height),
            )
        }
    }
}
