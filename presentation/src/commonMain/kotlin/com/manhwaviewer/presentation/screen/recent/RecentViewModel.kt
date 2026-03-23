package com.manhwaviewer.presentation.screen.recent

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import kotlinx.coroutines.launch

class RecentViewModel(
    getRecentArchivesUseCase: GetRecentArchivesUseCase,
) : MviViewModel<RecentState, RecentIntent, RecentSideEffect>(RecentState()) {

    init {
        viewModelScope.launch {
            getRecentArchivesUseCase().collect { archives ->
                updateState { copy(archives = sortArchives(archives), isLoading = false) }
            }
        }
    }

    override fun handleIntent(intent: RecentIntent) {
        when (intent) {
            is RecentIntent.OpenArchive -> {
                sendSideEffect(RecentSideEffect.NavigateToViewer(intent.archive.id))
            }
            is RecentIntent.SetSortField -> {
                updateState { copy(sortState = sortState.copy(field = intent.field)) }
                resortArchives()
            }
            RecentIntent.ToggleSortDirection -> {
                updateState { copy(sortState = sortState.copy(ascending = !sortState.ascending)) }
                resortArchives()
            }
            RecentIntent.ToggleViewMode -> {
                updateState {
                    copy(
                        sortState = sortState.copy(
                            viewMode = if (sortState.viewMode == ListViewMode.LIST) ListViewMode.GRID else ListViewMode.LIST,
                        ),
                    )
                }
            }
        }
    }

    private fun resortArchives() {
        updateState { copy(archives = sortArchives(archives)) }
    }

    private fun sortArchives(archives: List<ArchiveFile>): List<ArchiveFile> {
        val comparator = when (currentState.sortState.field) {
            SortField.NAME -> compareBy<ArchiveFile> { it.fileName.lowercase() }
            SortField.DATE -> compareBy { it.lastReadAt }
        }
        return if (currentState.sortState.ascending) {
            archives.sortedWith(comparator)
        } else {
            archives.sortedWith(comparator.reversed())
        }
    }
}
