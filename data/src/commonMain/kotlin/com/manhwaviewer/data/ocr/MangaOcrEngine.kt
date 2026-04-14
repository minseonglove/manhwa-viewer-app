package com.manhwaviewer.data.ocr

import com.manhwaviewer.data.ocr.image.PlatformImage

interface MangaOcrEngine {
    suspend fun recognizeText(imageBytes: ByteArray): String
    suspend fun recognizeText(image: PlatformImage): String
    fun close()
}

object MangaOcrConfig {
    const val IMAGE_SIZE = 224
    const val VOCAB_SIZE = 6144
    const val ENCODER_HIDDEN_SIZE = 192
    const val ENCODER_SEQ_LENGTH = 197
    const val MAX_LENGTH = 80
    const val DECODER_START_TOKEN_ID = 2
    const val EOS_TOKEN_ID = 3
    val SPECIAL_TOKEN_IDS = setOf(0, 1, 2, 3, 4)

    const val BEAM_WIDTH = 1
    const val REPETITION_PENALTY = 1.2f
    const val TEMPERATURE = 0.8f

    const val DECODER_NUM_LAYERS = 2
    const val DECODER_NUM_HEADS = 12
    const val DECODER_HEAD_DIM = 64

    const val YOLO_INPUT_SIZE = 640
    const val YOLO_MAX_DETECTIONS = 300
}
