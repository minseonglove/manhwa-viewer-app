package com.manhwaviewer.presentation.screen.settings

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ViewerPreference
import com.manhwaviewer.domain.usecase.ClearCacheUseCase
import com.manhwaviewer.domain.usecase.GetCacheSizeUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.UpdateViewerPreferenceUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getViewerPreferenceUseCase: GetViewerPreferenceUseCase,
    private val updateViewerPreferenceUseCase: UpdateViewerPreferenceUseCase,
    private val getCacheSizeUseCase: GetCacheSizeUseCase,
    private val clearCacheUseCase: ClearCacheUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsSideEffect>(SettingsState()) {

    init {
        viewModelScope.launch {
            getViewerPreferenceUseCase().collect { pref ->
                updateState { copy(preference = pref) }
            }
        }
        refreshCacheSize()
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateReadingDirection -> updatePref { copy(readingDirection = intent.direction) }
            is SettingsIntent.UpdateViewMode -> updatePref { copy(viewMode = intent.mode) }
            is SettingsIntent.UpdateKeepZoomState -> updatePref { copy(keepZoomState = intent.keep) }
            is SettingsIntent.UpdateBackgroundColor -> updatePref { copy(backgroundColor = intent.color) }
            is SettingsIntent.UpdateTranslationEngine -> updatePref { copy(defaultTranslationEngine = intent.engine) }
            is SettingsIntent.UpdateOfflineTranslation -> updatePref { copy(offlineTranslation = intent.enabled) }
            is SettingsIntent.UpdateAutoTranslate -> updatePref { copy(autoTranslateOnPageLoad = intent.enabled) }
            is SettingsIntent.ClearTranslationCache -> clearTranslationCache()
            is SettingsIntent.ClearImageCache -> clearImageCache()
            is SettingsIntent.RefreshCacheSize -> refreshCacheSize()
        }
    }

    private fun updatePref(transform: ViewerPreference.() -> ViewerPreference) {
        viewModelScope.launch {
            val newPref = transform(currentState.preference)
            updateState { copy(preference = newPref) }
            updateViewerPreferenceUseCase(newPref)
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
