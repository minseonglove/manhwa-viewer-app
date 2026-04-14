package com.manhwaviewer.data.ocr

actual object PlatformModelPaths {
    actual val ENCODER_MODEL_PATH = "mangaocr/encoder"
    actual val DECODER_MODEL_PATH = "mangaocr/decoder"
    actual val YOLO_MODEL_PATH = "mangaocr/yolo_detector"
    actual val VOCAB_FILE_PATH = "mangaocr/vocab"
    actual val CROSS_ATTN_INIT_PATH = "mangaocr/cross_attn_init"
    actual val DECODER_WITH_PAST_PATH = "mangaocr/decoder_with_past"
}
