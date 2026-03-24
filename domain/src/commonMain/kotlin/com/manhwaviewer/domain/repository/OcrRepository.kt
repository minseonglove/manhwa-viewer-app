package com.manhwaviewer.domain.repository

interface OcrRepository {
    suspend fun recognizeText(imageBytes: ByteArray): String
}
