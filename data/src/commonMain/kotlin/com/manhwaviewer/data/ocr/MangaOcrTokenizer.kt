package com.manhwaviewer.data.ocr

class MangaOcrTokenizer(vocabLines: List<String>) {
    private val idToToken: List<String> = vocabLines

    fun decode(tokenIds: List<Int>): String {
        return tokenIds
            .filter { it !in 0..4 }
            .mapNotNull { idToToken.getOrNull(it) }
            .joinToString("") { token ->
                if (token.startsWith("##")) token.substring(2) else token
            }
    }
}
