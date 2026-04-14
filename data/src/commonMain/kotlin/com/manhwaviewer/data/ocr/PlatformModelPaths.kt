package com.manhwaviewer.data.ocr

expect object PlatformModelPaths {
    val ENCODER_MODEL_PATH: String
    val DECODER_MODEL_PATH: String
    val YOLO_MODEL_PATH: String
    val VOCAB_FILE_PATH: String
    val CROSS_ATTN_INIT_PATH: String
    val DECODER_WITH_PAST_PATH: String
}
