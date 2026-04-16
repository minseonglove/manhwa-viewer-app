package com.manhwaviewer.presentation.screen.settings

import org.jetbrains.compose.resources.StringResource

data class SettingsState(
    val translationCacheSize: Long = 0L,
    val imageCacheSize: Long = 0L,
)

sealed interface SettingsIntent {
    data object ClearTranslationCache : SettingsIntent
    data object ClearImageCache : SettingsIntent
    data object RefreshCacheSize : SettingsIntent
}

sealed interface SettingsSideEffect {
    data class ShowToast(val messageRes: StringResource) : SettingsSideEffect
}
