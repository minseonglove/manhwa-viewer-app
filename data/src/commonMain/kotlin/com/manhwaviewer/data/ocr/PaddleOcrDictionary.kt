package com.manhwaviewer.data.ocr

class PaddleOcrDictionary(dictLines: List<String>) {
    // index 0 = CTC blank, index 1..N = dict lines 0..N-1
    private val indexToChar: List<String> = listOf("") + dictLines

    fun decode(indices: List<Int>): String =
        indices.mapNotNull { indexToChar.getOrNull(it) }.joinToString("")
}
