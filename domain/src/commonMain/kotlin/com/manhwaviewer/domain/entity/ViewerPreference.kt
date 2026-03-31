package com.manhwaviewer.domain.entity

data class ViewerPreference(
    val readingDirection: ReadingDirection = ReadingDirection.LTR,
    val viewMode: ViewMode = ViewMode.SINGLE_PAGE,
    val keepZoomState: Boolean = true,
    val backgroundColor: BackgroundColor = BackgroundColor.BLACK,
)

enum class ReadingDirection { LTR, RTL }
enum class ViewMode { WEBTOON, SINGLE_PAGE, DOUBLE_PAGE }
enum class BackgroundColor { BLACK, WHITE, AUTO }
