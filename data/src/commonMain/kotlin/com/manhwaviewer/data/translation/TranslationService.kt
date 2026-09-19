package com.manhwaviewer.data.translation

interface TranslationService {
    val engineName: String

    /** 반환 리스트는 [texts] 와 크기·순서가 같다. 그렇게 돌려줄 수 없으면 예외를 던진다. */
    suspend fun translate(texts: List<String>, source: String, target: String): List<String>
}
