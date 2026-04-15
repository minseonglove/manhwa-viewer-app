package com.manhwaviewer.presentation.screen.filemoveselect

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.filebrowser.NavigationDirection

data class FileMoveSelectState(
    val sourcePaths: List<String> = emptyList(),
    val startPath: String = "",
    val currentPath: String = "",
    val folders: List<ArchiveFile> = emptyList(),
    val pathSegments: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isMoving: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val defaultFolderName: String = "",
    val navigationDirection: NavigationDirection = NavigationDirection.FORWARD,
) {
    val isMoveEnabled: Boolean get() = currentPath != startPath && !isMoving
    val itemCount: Int get() = sourcePaths.size
}

sealed interface FileMoveSelectIntent {
    data class NavigateToFolder(val path: String) : FileMoveSelectIntent
    data object GoBack : FileMoveSelectIntent
    data class GoToSegment(val index: Int) : FileMoveSelectIntent
    data object ConfirmMove : FileMoveSelectIntent
    data object ShowCreateFolderDialog : FileMoveSelectIntent
    data object DismissCreateFolderDialog : FileMoveSelectIntent
    data class ConfirmCreateFolder(val folderName: String) : FileMoveSelectIntent
}

sealed interface FileMoveSelectSideEffect {
    data object MoveCompleted : FileMoveSelectSideEffect
    data class MoveError(val message: String) : FileMoveSelectSideEffect
    data object NavigateBack : FileMoveSelectSideEffect
}
