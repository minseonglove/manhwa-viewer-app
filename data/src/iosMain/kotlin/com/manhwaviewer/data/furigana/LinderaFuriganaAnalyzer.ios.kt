package com.manhwaviewer.data.furigana

import com.manhwaviewer.domain.entity.FuriganaToken
import com.manhwaviewer.domain.repository.FuriganaAnalyzer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import lindera.lindera_free
import lindera.lindera_init
import lindera.lindera_tokenize

@OptIn(ExperimentalForeignApi::class)
actual class LinderaFuriganaAnalyzer actual constructor() : FuriganaAnalyzer {

    private val initialized: Boolean = lindera_init() == 1

    actual override suspend fun analyze(text: String): List<FuriganaToken> {
        if (!initialized || text.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val resultPtr = lindera_tokenize(text) ?: return@withContext emptyList()
            val json = resultPtr.toKString()
            lindera_free(resultPtr)
            FuriganaJsonParser.parse(json)
        }
    }
}
