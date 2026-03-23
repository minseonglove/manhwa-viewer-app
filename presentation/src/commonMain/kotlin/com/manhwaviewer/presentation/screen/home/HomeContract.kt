package com.manhwaviewer.presentation.screen.home

data class HomeState(
    val recentCount: Int = 0,
    val imageCount: Int = 0,
    val vaultFolderCount: Int = 0,
    val isLoading: Boolean = false,
)

sealed interface HomeIntent {
    data object NavigateToFiles : HomeIntent
    data object NavigateToRecent : HomeIntent
    data object NavigateToImages : HomeIntent
    data object NavigateToVault : HomeIntent
    data object NavigateToSettings : HomeIntent
}

sealed interface HomeSideEffect {
    data class NavigateToViewer(val archiveId: Long) : HomeSideEffect
    data object NavigateToSettings : HomeSideEffect
    data object NavigateToFiles : HomeSideEffect
    data object NavigateToRecent : HomeSideEffect
    data object NavigateToImages : HomeSideEffect
    data object NavigateToVault : HomeSideEffect
}
