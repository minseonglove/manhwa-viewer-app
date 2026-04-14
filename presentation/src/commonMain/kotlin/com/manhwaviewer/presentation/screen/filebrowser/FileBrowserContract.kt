package com.manhwaviewer.presentation.screen.filebrowser

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.SortState
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel

enum class NavigationDirection { FORWARD, BACKWARD }

data class FileBrowserState(
    val files: List<ArchiveFile> = emptyList(),
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val sortState: SortState = SortState(),
    val isLoading: Boolean = false,
    val navigationDirection: NavigationDirection = NavigationDirection.FORWARD,
    val insideArchivePath: String? = null,
    val coverModels: Map<String, ViewerPageModel> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedFileKeys: Set<String> = emptySet(),
    val showRenameDialog: Boolean = false,
    val renameTarget: ArchiveFile? = null,
    val showDeleteDialog: Boolean = false,
    val deleteTargets: List<ArchiveFile> = emptyList(),
)

sealed interface FileBrowserIntent {
    data class NavigateToFolder(val path: String) : FileBrowserIntent
    data class OpenFile(val archive: ArchiveFile) : FileBrowserIntent
    data class OpenArchiveEntry(val archive: ArchiveFile, val pageIndex: Int) : FileBrowserIntent
    data class SetSortField(val field: com.manhwaviewer.presentation.screen.common.SortField) : FileBrowserIntent
    data object ToggleSortDirection : FileBrowserIntent
    data object ToggleViewMode : FileBrowserIntent
    data object GoBack : FileBrowserIntent
    data object GoHome : FileBrowserIntent
    data class GoToSegment(val index: Int) : FileBrowserIntent
    data class EnterSelectionMode(val file: ArchiveFile) : FileBrowserIntent
    data class ToggleSelection(val file: ArchiveFile) : FileBrowserIntent
    data object ToggleSelectAll : FileBrowserIntent
    data object ExitSelectionMode : FileBrowserIntent
    data object MoveSelected : FileBrowserIntent
    data object VaultSelected : FileBrowserIntent
    data object DeleteSelected : FileBrowserIntent
    data object RenameSelected : FileBrowserIntent
    data class ConfirmRename(val newName: String) : FileBrowserIntent
    data object DismissRenameDialog : FileBrowserIntent
    data object ConfirmDelete : FileBrowserIntent
    data object DismissDeleteDialog : FileBrowserIntent
}

sealed interface FileBrowserSideEffect {
    data class NavigateToViewer(val archiveId: Long, val startPage: Int = 0, val sortByDate: Boolean = false, val sortAscending: Boolean = true) : FileBrowserSideEffect
    data object NavigateToHome : FileBrowserSideEffect
}
