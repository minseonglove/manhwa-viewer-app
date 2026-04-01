package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.TranslationRepository

class TranslateTextUseCase(
    private val repository: TranslationRepository,
) {
    suspend operator fun invoke(
        texts: List<String>,
        sourceLanguage: String = "ja",
        targetLanguage: String = "ko",
    ): List<String> {
        return repository.translateBatch(texts, sourceLanguage, targetLanguage)
    }
}
