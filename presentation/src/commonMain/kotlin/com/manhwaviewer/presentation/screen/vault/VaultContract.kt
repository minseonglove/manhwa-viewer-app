package com.manhwaviewer.presentation.screen.vault

import com.manhwaviewer.domain.entity.VaultFile
import com.manhwaviewer.presentation.screen.common.SortField
import com.manhwaviewer.presentation.screen.common.SortState

data class VaultState(
    val files: List<VaultFile> = emptyList(),
    val currentPath: String = "",
    val sortState: SortState = SortState(),
    val isLoading: Boolean = true,
)

sealed interface VaultIntent {
    data class OpenFile(val file: VaultFile) : VaultIntent
    data class SetSortField(val field: SortField) : VaultIntent
    data object ToggleSortDirection : VaultIntent
    data object ToggleViewMode : VaultIntent
}

sealed interface VaultSideEffect {
    data class NavigateToViewer(val archiveId: Long) : VaultSideEffect
}
