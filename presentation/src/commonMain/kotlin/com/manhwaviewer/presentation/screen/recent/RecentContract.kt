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
    val bottomSheetType: RecentBottomSheetType = RecentBottomSheetType.None,
    val isSelectionMode: Boolean = false,
    val selectedArchiveIds: Set<Long> = emptySet(),
)

sealed interface RecentBottomSheetType {
    data object None : RecentBottomSheetType
    data class DeleteSingle(val archive: ArchiveFile) : RecentBottomSheetType
    data object ClearAll : RecentBottomSheetType
    data class DeleteSelected(val archiveIds: Set<Long>) : RecentBottomSheetType
}

sealed interface RecentIntent {
    data class OpenArchive(val archive: ArchiveFile) : RecentIntent
    data class SetSortField(val field: SortField) : RecentIntent
    data object ToggleSortDirection : RecentIntent
    data object ToggleViewMode : RecentIntent
    data class ShowDeleteConfirm(val archive: ArchiveFile) : RecentIntent
    data object ShowClearAllConfirm : RecentIntent
    data object DismissBottomSheet : RecentIntent
    data object ConfirmAction : RecentIntent
    data class EnterSelectionMode(val archive: ArchiveFile) : RecentIntent
    data class ToggleSelection(val archive: ArchiveFile) : RecentIntent
    data object ExitSelectionMode : RecentIntent
    data object ShowDeleteSelectedConfirm : RecentIntent
}

sealed interface RecentSideEffect {
    data class NavigateToViewer(val archiveId: Long) : RecentSideEffect
}
