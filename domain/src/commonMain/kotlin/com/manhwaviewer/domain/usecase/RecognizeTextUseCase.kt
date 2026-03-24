package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.repository.OcrRepository

class RecognizeTextUseCase(
    private val repository: OcrRepository,
) {
    suspend operator fun invoke(imageBytes: ByteArray): String {
        return repository.recognizeText(imageBytes)
    }
}
