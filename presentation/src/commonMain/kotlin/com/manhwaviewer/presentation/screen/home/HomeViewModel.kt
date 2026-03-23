package com.manhwaviewer.presentation.screen.home

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesCountUseCase
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

class HomeViewModel(
    getRecentArchivesCountUseCase: GetRecentArchivesCountUseCase,
    getDeviceImagesUseCase: GetDeviceImagesUseCase,
    getVaultFilesUseCase: GetVaultFilesUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeSideEffect>(HomeState()) {

    init {
        viewModelScope.launch {
            getRecentArchivesCountUseCase().collect { count ->
                updateState { copy(recentCount = count) }
            }
        }
        viewModelScope.launch {
            getDeviceImagesUseCase().collect { images ->
                updateState { copy(imageCount = images.size) }
            }
        }
        viewModelScope.launch {
            getVaultFilesUseCase().collect { vaultFiles ->
                updateState { copy(vaultFolderCount = vaultFiles.size) }
            }
        }
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.NavigateToFiles -> sendSideEffect(HomeSideEffect.NavigateToFiles)
            HomeIntent.NavigateToRecent -> sendSideEffect(HomeSideEffect.NavigateToRecent)
            HomeIntent.NavigateToImages -> sendSideEffect(HomeSideEffect.NavigateToImages)
            HomeIntent.NavigateToVault -> sendSideEffect(HomeSideEffect.NavigateToVault)
            HomeIntent.NavigateToSettings -> sendSideEffect(HomeSideEffect.NavigateToSettings)
        }
    }
}
