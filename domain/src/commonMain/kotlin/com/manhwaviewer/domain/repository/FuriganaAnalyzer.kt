package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.FuriganaToken

interface FuriganaAnalyzer {
    suspend fun analyze(text: String): List<FuriganaToken>
}
