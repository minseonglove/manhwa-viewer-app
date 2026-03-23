package com.manhwaviewer.domain.entity

data class KanaResult(
    val fullReading: String,
    val tokens: List<TokenReading>
)

data class TokenReading(
    val surface: String,
    val reading: String
)
