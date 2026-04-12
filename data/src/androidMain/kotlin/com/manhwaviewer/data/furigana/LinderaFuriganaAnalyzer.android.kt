package com.manhwaviewer.data.furigana

import com.manhwaviewer.domain.entity.FuriganaToken
import com.manhwaviewer.domain.repository.FuriganaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

actual class LinderaFuriganaAnalyzer actual constructor() : FuriganaAnalyzer {

    private var initialized = false

    init {
        try {
            System.loadLibrary("lindera_ffi")
            initialized = nativeInit() == 1
        } catch (_: UnsatisfiedLinkError) {
            initialized = false
        }
    }

    private external fun nativeInit(): Int
    private external fun nativeTokenize(text: String): String?
    private external fun nativeDestroy()

    override suspend fun analyze(text: String): List<FuriganaToken> {
        if (!initialized || text.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val json = nativeTokenize(text) ?: return@withContext emptyList()
            FuriganaJsonParser.parse(json)
        }
    }
}
