package com.manhwaviewer.data.ocr

interface MangaOcrEngine {
    suspend fun recognizeText(imageBytes: ByteArray): String
    fun close()
}

object MangaOcrConfig {
    const val IMAGE_SIZE = 224
    const val VOCAB_SIZE = 6144
    const val ENCODER_HIDDEN_SIZE = 192
    const val ENCODER_SEQ_LENGTH = 197
    const val MAX_LENGTH = 300
    const val DECODER_START_TOKEN_ID = 2
    const val EOS_TOKEN_ID = 3
    val SPECIAL_TOKEN_IDS = setOf(0, 1, 2, 3, 4)

    const val ENCODER_MODEL = "mangaocr/encoder_model_fp16.onnx"
    const val DECODER_MODEL = "mangaocr/decoder_model_fp16.onnx"
    const val VOCAB_FILE = "mangaocr/vocab.txt"
    const val CTD_MODEL = "mangaocr/comic-text-detector_fp16.onnx"
    const val CTD_INPUT_SIZE = 1024
    const val CTD_NUM_ANCHORS = 64512

    const val YOLO_MODEL = "mangaocr/manga_panel_detector_fp16.onnx"
    const val YOLO_INPUT_SIZE = 640
    const val YOLO_MAX_DETECTIONS = 300
}
