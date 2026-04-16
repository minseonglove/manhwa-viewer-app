package com.manhwaviewer.presentation.screen.search

import com.manhwaviewer.domain.entity.ArchiveFile

data class SearchState(
    val query: String = "",
    val folders: List<ArchiveFile> = emptyList(),
    val archives: List<ArchiveFile> = emptyList(),
    val images: List<ArchiveFile> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data object ClearQuery : SearchIntent
    data class OpenFile(val file: ArchiveFile) : SearchIntent
}

sealed interface SearchSideEffect {
    data class NavigateToFileBrowser(val path: String) : SearchSideEffect
    data class NavigateToViewer(
        val archiveId: Long,
        val startPage: Int = 0,
        val sortByDate: Boolean = false,
        val sortAscending: Boolean = true,
    ) : SearchSideEffect
}
