package com.manhwaviewer.domain.repository

import com.manhwaviewer.domain.entity.KanaResult

interface JapaneseTextRepository {
    suspend fun toKanaReading(sentence: String): KanaResult
}
