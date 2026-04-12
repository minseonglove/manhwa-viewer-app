package com.manhwaviewer.data.furigana

import com.manhwaviewer.domain.entity.FuriganaToken
import com.manhwaviewer.domain.repository.FuriganaAnalyzer

expect class LinderaFuriganaAnalyzer() : FuriganaAnalyzer {
    override suspend fun analyze(text: String): List<FuriganaToken>
}
