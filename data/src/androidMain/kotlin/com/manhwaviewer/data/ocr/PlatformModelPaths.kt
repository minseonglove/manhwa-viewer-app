package com.manhwaviewer.data.ocr

actual object PlatformModelPaths {
    actual val ENCODER_MODEL_PATH = "mangaocr/encoder_model_fp16.onnx"
    actual val DECODER_MODEL_PATH = "mangaocr/decoder_model_fp16.onnx"
    actual val YOLO_MODEL_PATH = "mangaocr/manga_panel_detector_fp16.onnx"
    actual val VOCAB_FILE_PATH = "mangaocr/vocab.txt"
}
