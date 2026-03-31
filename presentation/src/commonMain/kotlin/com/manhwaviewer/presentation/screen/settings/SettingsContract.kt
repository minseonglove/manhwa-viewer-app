package com.manhwaviewer.presentation.screen.settings

import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TranslationEngine
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference

data class SettingsState(
    val preference: ViewerPreference = ViewerPreference(),
    val translationCacheSize: Long = 0L,
    val imageCacheSize: Long = 0L,
)

sealed interface SettingsIntent {
    data class UpdateReadingDirection(val direction: ReadingDirection) : SettingsIntent
    data class UpdateViewMode(val mode: ViewMode) : SettingsIntent
    data class UpdateKeepZoomState(val keep: Boolean) : SettingsIntent
    data class UpdateBackgroundColor(val color: BackgroundColor) : SettingsIntent
    data class UpdateTranslationEngine(val engine: TranslationEngine) : SettingsIntent
    data class UpdateOfflineTranslation(val enabled: Boolean) : SettingsIntent
    data class UpdateAutoTranslate(val enabled: Boolean) : SettingsIntent
    data object ClearTranslationCache : SettingsIntent
    data object ClearImageCache : SettingsIntent
    data object RefreshCacheSize : SettingsIntent
}

sealed interface SettingsSideEffect {
    data class ShowToast(val message: String) : SettingsSideEffect
}
