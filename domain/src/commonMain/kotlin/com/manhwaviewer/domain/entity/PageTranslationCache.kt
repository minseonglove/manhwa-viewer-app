package com.manhwaviewer.domain.entity

data class OcrBlock(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: String,
    val dominantColorArgb: Int? = null,
)

data class PageOcrCache(
    val id: Long = 0,
    val archiveId: Long,
    val entryName: String,
    val page: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<OcrBlock>,
    val createdAt: Long,
)

data class TranslationBlock(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val originalText: String,
    val translatedText: String,
    val dominantColorArgb: Int? = null,
)

data class PageTranslationCache(
    val id: Long = 0,
    val archiveId: Long,
    val entryName: String,
    val page: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<TranslationBlock>,
    val createdAt: Long,
)
