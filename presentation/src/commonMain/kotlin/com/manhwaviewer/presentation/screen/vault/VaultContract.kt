package com.manhwaviewer.presentation.screen.vault

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.SortState
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel

data class VaultState(
    val files: List<ArchiveFile> = emptyList(),
    val sortState: SortState = SortState(),
    val isLoading: Boolean = true,
    val coverModels: Map<Long, ViewerPageModel> = emptyMap(),
)

sealed interface VaultIntent {
    data class OpenFile(val archive: ArchiveFile) : VaultIntent
    data class SetSortField(val field: com.manhwaviewer.presentation.screen.common.SortField) : VaultIntent
    data object ToggleSortDirection : VaultIntent
    data object ToggleViewMode : VaultIntent
}

sealed interface VaultSideEffect {
    data class NavigateToViewer(val archiveId: Long) : VaultSideEffect
}
