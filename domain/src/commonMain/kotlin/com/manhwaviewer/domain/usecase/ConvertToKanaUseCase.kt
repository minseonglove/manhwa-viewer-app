package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.KanaResult
import com.manhwaviewer.domain.repository.JapaneseTextRepository

class ConvertToKanaUseCase(
    private val repository: JapaneseTextRepository,
) {
    suspend operator fun invoke(sentence: String): KanaResult {
        return repository.toKanaReading(sentence)
    }
}
