package com.manhwaviewer.domain.repository

interface TranslationRepository {
    /**
     * 텍스트를 번역합니다. 캐시가 있으면 캐시를 반환합니다.
     * Google 번역 → Papago 번역 파이프라인으로 동작합니다.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String = "ja",
        targetLanguage: String = "ko",
    ): String

    /**
     * 여러 텍스트를 일괄 번역합니다. 각 텍스트별 캐시를 활용합니다.
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String = "ja",
        targetLanguage: String = "ko",
    ): List<String>
}
