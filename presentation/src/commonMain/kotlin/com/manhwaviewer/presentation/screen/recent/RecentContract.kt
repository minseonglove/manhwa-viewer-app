package com.manhwaviewer.presentation.screen.recent

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.SortField
import com.manhwaviewer.presentation.screen.common.SortState
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel

data class RecentState(
    val archives: List<ArchiveFile> = emptyList(),
    val sortState: SortState = SortState(field = SortField.DATE, ascending = false),
    val isLoading: Boolean = true,
    val coverModels: Map<Long, ViewerPageModel> = emptyMap(),
)

sealed interface RecentIntent {
    data class OpenArchive(val archive: ArchiveFile) : RecentIntent
    data class SetSortField(val field: SortField) : RecentIntent
    data object ToggleSortDirection : RecentIntent
    data object ToggleViewMode : RecentIntent
}

sealed interface RecentSideEffect {
    data class NavigateToViewer(val archiveId: Long) : RecentSideEffect
}
