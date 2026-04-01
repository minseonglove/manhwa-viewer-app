package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.repository.TranslationRepository

class TranslateTextUseCase(
    private val repository: TranslationRepository,
) {
    suspend operator fun invoke(
        texts: List<String>,
        sourceLanguage: String = "ja",
        targetLanguage: String = "ko",
    ): List<TranslationResult> {
        return repository.translateBatch(texts, sourceLanguage, targetLanguage)
    }
}
