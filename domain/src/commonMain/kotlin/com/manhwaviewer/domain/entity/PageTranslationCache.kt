package com.manhwaviewer.domain.entity

data class TranslationBlock(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val originalText: String,
    val translatedText: String,
)

data class PageTranslationCache(
    val id: Long = 0,
    val archiveId: Long,
    val page: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<TranslationBlock>,
    val createdAt: Long,
)
