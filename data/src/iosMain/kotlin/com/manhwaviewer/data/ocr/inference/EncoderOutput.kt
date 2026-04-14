package com.manhwaviewer.data.ocr.inference

import platform.CoreML.MLMultiArray

actual class EncoderOutput(
    val multiArray: MLMultiArray,
)
