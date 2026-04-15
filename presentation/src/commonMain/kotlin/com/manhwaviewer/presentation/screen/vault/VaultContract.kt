package com.manhwaviewer.presentation.screen.vault

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.SortField
import com.manhwaviewer.presentation.screen.common.SortState

data class VaultState(
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val sortState: SortState = SortState(),
    val isLoading: Boolean = true,
    val insideArchivePath: String? = null,
    val archiveFiles: List<ArchiveFile> = emptyList(),
    val navigationDirection: NavigationDirection = NavigationDirection.FORWARD,
    val isSelectionMode: Boolean = false,
    val selectedFileKeys: Set<String> = emptySet(),
    val showRenameDialog: Boolean = false,
    val renameTarget: ArchiveFile? = null,
    val showDeleteDialog: Boolean = false,
    val deleteTargets: List<ArchiveFile> = emptyList(),
    val showRestoreDialog: Boolean = false,
    val restoreTargets: List<ArchiveFile> = emptyList(),
)

enum class NavigationDirection { FORWARD, BACKWARD }

sealed interface VaultIntent {
    data class OpenFile(val file: ArchiveFile) : VaultIntent
    data class OpenArchiveEntry(val file: ArchiveFile, val pageIndex: Int) : VaultIntent
    data class NavigateToFolder(val path: String) : VaultIntent
    data object GoBack : VaultIntent
    data class GoToSegment(val index: Int) : VaultIntent
    data class SetSortField(val field: SortField) : VaultIntent
    data object ToggleSortDirection : VaultIntent
    data object ToggleViewMode : VaultIntent

    // 선택 모드
    data class EnterSelectionMode(val file: ArchiveFile) : VaultIntent
    data class ToggleSelection(val file: ArchiveFile) : VaultIntent
    data object ToggleSelectAll : VaultIntent
    data object ExitSelectionMode : VaultIntent

    // 선택 모드 액션
    data object MoveSelected : VaultIntent
    data object RestoreSelected : VaultIntent
    data object DeleteSelected : VaultIntent
    data object RenameSelected : VaultIntent
    data class ConfirmRename(val newName: String) : VaultIntent
    data object DismissRenameDialog : VaultIntent
    data object ConfirmDelete : VaultIntent
    data object DismissDeleteDialog : VaultIntent
    data object ConfirmRestore : VaultIntent
    data object DismissRestoreDialog : VaultIntent
}

sealed interface VaultSideEffect {
    data class NavigateToViewer(
        val archiveId: Long,
        val startPage: Int = -1,
        val sortByDate: Boolean = false,
        val sortAscending: Boolean = true,
    ) : VaultSideEffect
    data object NavigateBack : VaultSideEffect
    data object NavigateToMoveSelect : VaultSideEffect
}
