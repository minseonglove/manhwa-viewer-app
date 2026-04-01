package com.manhwaviewer.data.ocr

object PaddleOcrConfig {
    const val DET_MODEL = "paddleocr/ppocr_v5_det.onnx"
    const val REC_MODEL = "paddleocr/ppocr_v5_rec.onnx"
    const val DICT_FILE = "paddleocr/ppocr_v5_dict.txt"

    // Det 모델 입력 크기
    const val DET_MAX_SIDE = 960
    const val DET_STRIDE = 32

    // DB 후처리 임계값
    const val DB_THRESH = 0.3f
    const val DB_BOX_THRESH = 0.6f
    const val DB_UNCLIP_RATIO = 1.5f
    const val DB_MIN_SIZE = 3

    // Rec 모델 입력 크기
    const val REC_IMAGE_HEIGHT = 48
    const val REC_MAX_WIDTH = 320
}
