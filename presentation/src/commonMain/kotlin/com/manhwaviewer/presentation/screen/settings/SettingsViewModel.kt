package com.manhwaviewer.presentation.screen.settings

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.ClearCacheUseCase
import com.manhwaviewer.domain.usecase.GetCacheSizeUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

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
            sendSideEffect(SettingsSideEffect.ShowToast("번역 캐시가 삭제되었습니다"))
        }
    }

    private fun clearImageCache() {
        viewModelScope.launch {
            clearCacheUseCase.imageCache()
            refreshCacheSize()
            sendSideEffect(SettingsSideEffect.ShowToast("이미지 캐시가 삭제되었습니다"))
        }
    }
}
