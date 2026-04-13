package com.manhwaviewer.presentation.screen.recent

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.ClearAllRecentHistoryUseCase
import com.manhwaviewer.domain.usecase.ClearRecentHistoryUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetRecentArchivesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class RecentViewModel(
    getRecentArchivesUseCase: GetRecentArchivesUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val clearRecentHistoryUseCase: ClearRecentHistoryUseCase,
    private val clearAllRecentHistoryUseCase: ClearAllRecentHistoryUseCase,
) : MviViewModel<RecentState, RecentIntent, RecentSideEffect>(RecentState()) {

    init {
        viewModelScope.launch {
            getRecentArchivesUseCase().collect { archives ->
                val sorted = sortArchives(archives)
                updateState { copy(archives = sorted, isLoading = false) }
                loadCoverModels(sorted)
            }
        }
    }

    private suspend fun loadCoverModels(archives: List<ArchiveFile>) {
        val currentModels = state.value.coverModels
        val targets = archives.filter {
            it.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR, ArchiveType.FOLDER) && it.id !in currentModels
        }
        if (targets.isEmpty()) return
        supervisorScope {
            val newResults = targets.map { archive ->
                async {
                    try {
                        val entries = getImageEntriesUseCase(archive.filePath)
                        if (entries.isNotEmpty()) {
                            archive.id to ViewerPageModel(
                                archivePath = archive.filePath,
                                entryNames = listOf(entries.first()),
                            )
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
            }.mapNotNull { it.await() }.toMap()
            updateState { copy(coverModels = currentModels + newResults) }
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
            is RecentIntent.ShowDeleteConfirm -> {
                updateState { copy(bottomSheetType = RecentBottomSheetType.DeleteSingle(intent.archive)) }
            }
            RecentIntent.ShowClearAllConfirm -> {
                updateState { copy(bottomSheetType = RecentBottomSheetType.ClearAll) }
            }
            RecentIntent.DismissBottomSheet -> {
                updateState { copy(bottomSheetType = RecentBottomSheetType.None) }
            }
            RecentIntent.ConfirmAction -> {
                val sheetType = currentState.bottomSheetType
                updateState { copy(bottomSheetType = RecentBottomSheetType.None) }
                viewModelScope.launch {
                    when (sheetType) {
                        is RecentBottomSheetType.DeleteSingle -> {
                            clearRecentHistoryUseCase(sheetType.archive.id)
                        }
                        RecentBottomSheetType.ClearAll -> {
                            clearAllRecentHistoryUseCase()
                        }
                        is RecentBottomSheetType.DeleteSelected -> {
                            sheetType.archiveIds.forEach { id ->
                                clearRecentHistoryUseCase(id)
                            }
                            updateState { copy(isSelectionMode = false, selectedArchiveIds = emptySet()) }
                        }
                        RecentBottomSheetType.None -> Unit
                    }
                }
            }
            is RecentIntent.EnterSelectionMode -> {
                updateState {
                    copy(
                        isSelectionMode = true,
                        selectedArchiveIds = setOf(intent.archive.id),
                    )
                }
            }
            is RecentIntent.ToggleSelection -> {
                val newSet = currentState.selectedArchiveIds.toMutableSet()
                val id = intent.archive.id
                if (id in newSet) newSet.remove(id) else newSet.add(id)
                if (newSet.isEmpty()) {
                    updateState { copy(isSelectionMode = false, selectedArchiveIds = emptySet()) }
                } else {
                    updateState { copy(selectedArchiveIds = newSet) }
                }
            }
            RecentIntent.ExitSelectionMode -> {
                updateState { copy(isSelectionMode = false, selectedArchiveIds = emptySet()) }
            }
            RecentIntent.ShowDeleteSelectedConfirm -> {
                updateState {
                    copy(bottomSheetType = RecentBottomSheetType.DeleteSelected(selectedArchiveIds))
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
