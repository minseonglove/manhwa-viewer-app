package com.manhwaviewer.presentation.screen.settings

import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference

data class SettingsState(
    val preference: ViewerPreference = ViewerPreference(),
)

sealed interface SettingsIntent {
    data class UpdateReadingDirection(val direction: ReadingDirection) : SettingsIntent
    data class UpdateViewMode(val mode: ViewMode) : SettingsIntent
    data class UpdateKeepZoomState(val keep: Boolean) : SettingsIntent
    data class UpdateBackgroundColor(val color: BackgroundColor) : SettingsIntent
}

sealed interface SettingsSideEffect
