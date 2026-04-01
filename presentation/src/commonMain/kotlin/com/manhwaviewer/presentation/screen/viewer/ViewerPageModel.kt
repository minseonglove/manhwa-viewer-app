package com.manhwaviewer.presentation.screen.viewer

/**
 * Coil custom Fetcher용 이미지 모델.
 * - 단일 페이지: entryNames 크기 1
 * - 양면 페이지: entryNames 크기 2 (합성 순서대로)
 */
data class ViewerPageModel(
    val archivePath: String,
    val entryNames: List<String>,
    val isRtl: Boolean = false,
)
