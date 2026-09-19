package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.TranslationResult

interface TranslationRepository {
    /**
     * 텍스트를 번역합니다. 캐시가 있으면 캐시를 반환합니다.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String = "ja",
        targetLanguage: String = "ko",
    ): String

    /**
     * 여러 텍스트를 일괄 번역합니다.
     * 캐시된 텍스트는 건너뛰고, 미캐시 텍스트만 모아 번역합니다. 결과는 [texts] 와 순서가 같습니다.
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String = "ja",
        targetLanguage: String = "ko",
    ): List<TranslationResult>
}
