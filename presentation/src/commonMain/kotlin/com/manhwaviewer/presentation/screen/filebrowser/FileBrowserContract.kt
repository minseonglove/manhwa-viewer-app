package com.manhwaviewer.presentation.screen.filebrowser

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.SortState

enum class NavigationDirection { FORWARD, BACKWARD }

data class FileBrowserState(
    val files: List<ArchiveFile> = emptyList(),
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val sortState: SortState = SortState(),
    val isLoading: Boolean = false,
    val navigationDirection: NavigationDirection = NavigationDirection.FORWARD,
    val insideArchivePath: String? = null,
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
}

sealed interface FileBrowserSideEffect {
    data class NavigateToViewer(val archiveId: Long, val startPage: Int = 0) : FileBrowserSideEffect
    data object NavigateToHome : FileBrowserSideEffect
}
