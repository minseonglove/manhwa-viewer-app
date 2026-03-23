package com.manhwaviewer.presentation.screen.settings

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ViewerPreference
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.UpdateViewerPreferenceUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getViewerPreferenceUseCase: GetViewerPreferenceUseCase,
    private val updateViewerPreferenceUseCase: UpdateViewerPreferenceUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsSideEffect>(SettingsState()) {

    init {
        viewModelScope.launch {
            getViewerPreferenceUseCase().collect { pref ->
                updateState { copy(preference = pref) }
            }
        }
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.UpdateReadingDirection -> updatePref { copy(readingDirection = intent.direction) }
            is SettingsIntent.UpdateViewMode -> updatePref { copy(viewMode = intent.mode) }
            is SettingsIntent.UpdateKeepZoomState -> updatePref { copy(keepZoomState = intent.keep) }
            is SettingsIntent.UpdateBackgroundColor -> updatePref { copy(backgroundColor = intent.color) }
        }
    }

    private fun updatePref(transform: ViewerPreference.() -> ViewerPreference) {
        viewModelScope.launch {
            val newPref = transform(currentState.preference)
            updateState { copy(preference = newPref) }
            updateViewerPreferenceUseCase(newPref)
        }
    }
}
