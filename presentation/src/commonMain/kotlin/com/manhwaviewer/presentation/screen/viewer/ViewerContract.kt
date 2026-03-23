package com.manhwaviewer.presentation.screen.viewer

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ViewerPreference

data class ViewerState(
    val archive: ArchiveFile? = null,
    val filePath: String = "",
    val imageEntries: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val preference: ViewerPreference = ViewerPreference(),
    val showControls: Boolean = false,
    val isLoading: Boolean = true,
)

sealed interface ViewerIntent {
    data class LoadArchive(val archiveId: Long, val startPage: Int = 0) : ViewerIntent
    data class SetPage(val page: Int) : ViewerIntent
    data object NextPage : ViewerIntent
    data object PreviousPage : ViewerIntent
    data object ToggleControls : ViewerIntent
    data object SaveProgress : ViewerIntent
}

sealed interface ViewerSideEffect {
    data object NavigateBack : ViewerSideEffect
}
