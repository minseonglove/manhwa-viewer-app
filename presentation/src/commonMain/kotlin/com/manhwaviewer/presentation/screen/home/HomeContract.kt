package com.manhwaviewer.presentation.screen.home

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel

data class HomeState(
    val imageCount: Int = 0,
    val vaultFolderCount: Int = 0,
    val isLoading: Boolean = false,
    val continueReading: ArchiveFile? = null,
    val continueReadingCoverModel: ViewerPageModel? = null,
    val recentArchives: List<ArchiveFile> = emptyList(),
    val recentCoverModels: Map<Long, ViewerPageModel> = emptyMap(),
)

sealed interface HomeIntent {
    data object NavigateToFiles : HomeIntent
    data object NavigateToRecent : HomeIntent
    data object NavigateToImages : HomeIntent
    data object NavigateToVault : HomeIntent
    data object NavigateToSettings : HomeIntent
    data class OpenArchive(val archiveId: Long) : HomeIntent
}

sealed interface HomeSideEffect {
    data class NavigateToViewer(val archiveId: Long) : HomeSideEffect
    data object NavigateToSettings : HomeSideEffect
    data object NavigateToFiles : HomeSideEffect
    data object NavigateToRecent : HomeSideEffect
    data object NavigateToImages : HomeSideEffect
    data object NavigateToVault : HomeSideEffect
}
