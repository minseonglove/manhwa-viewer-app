package com.manhwaviewer.presentation.screen.settings

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.ClearCacheUseCase
import com.manhwaviewer.domain.usecase.GetCacheSizeUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.settings_image_cache_cleared
import manhwa_viewer.presentation.generated.resources.settings_translation_cache_cleared

class SettingsViewModel(
    private val getCacheSizeUseCase: GetCacheSizeUseCase,
    private val clearCacheUseCase: ClearCacheUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsSideEffect>(SettingsState()) {

    init {
        refreshCacheSize()
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ClearTranslationCache -> clearTranslationCache()
            is SettingsIntent.ClearImageCache -> clearImageCache()
            is SettingsIntent.RefreshCacheSize -> refreshCacheSize()
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            val translationSize = getCacheSizeUseCase.translationCache()
            val imageSize = getCacheSizeUseCase.imageCache()
            updateState { copy(translationCacheSize = translationSize, imageCacheSize = imageSize) }
        }
    }

    private fun clearTranslationCache() {
        viewModelScope.launch {
            clearCacheUseCase.translationCache()
            refreshCacheSize()
            sendSideEffect(SettingsSideEffect.ShowToast(Res.string.settings_translation_cache_cleared))
        }
    }

    private fun clearImageCache() {
        viewModelScope.launch {
            clearCacheUseCase.imageCache()
            refreshCacheSize()
            sendSideEffect(SettingsSideEffect.ShowToast(Res.string.settings_image_cache_cleared))
        }
    }
}
