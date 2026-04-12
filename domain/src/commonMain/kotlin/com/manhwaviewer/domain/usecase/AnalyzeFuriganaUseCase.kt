package com.manhwaviewer.domain.usecase

import com.manhwaviewer.domain.entity.FuriganaToken
import com.manhwaviewer.domain.repository.FuriganaAnalyzer

class AnalyzeFuriganaUseCase(
    private val analyzer: FuriganaAnalyzer,
) {
    suspend operator fun invoke(text: String): List<FuriganaToken> =
        analyzer.analyze(text)
}
