package com.manhwaviewer.presentation.screen.vault

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class VaultViewModel(
    getVaultFilesUseCase: GetVaultFilesUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
) : MviViewModel<VaultState, VaultIntent, VaultSideEffect>(VaultState()) {

    init {
        viewModelScope.launch {
            getVaultFilesUseCase().collect { files ->
                val sorted = sortFiles(files)
                updateState { copy(files = sorted, isLoading = false) }
                loadCoverModels(sorted)
            }
        }
    }

    private suspend fun loadCoverModels(files: List<ArchiveFile>) {
        val currentModels = state.value.coverModels
        val targets = files.filter {
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

    override fun handleIntent(intent: VaultIntent) {
        when (intent) {
            is VaultIntent.OpenFile -> openFile(intent.archive)
            is VaultIntent.SetSortField -> {
                updateState { copy(sortState = sortState.copy(field = intent.field)) }
                resortFiles()
            }
            VaultIntent.ToggleSortDirection -> {
                updateState { copy(sortState = sortState.copy(ascending = !sortState.ascending)) }
                resortFiles()
            }
            VaultIntent.ToggleViewMode -> {
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

    private fun openFile(archive: ArchiveFile) {
        viewModelScope.launch {
            val id = upsertArchiveUseCase(archive)
            sendSideEffect(VaultSideEffect.NavigateToViewer(id))
        }
    }

    private fun resortFiles() {
        updateState { copy(files = sortFiles(files)) }
    }

    private fun sortFiles(files: List<ArchiveFile>): List<ArchiveFile> {
        val comparator = when (currentState.sortState.field) {
            SortField.NAME -> compareBy<ArchiveFile> { it.fileName.lowercase() }
            SortField.DATE -> compareBy { it.addedAt }
        }
        return if (currentState.sortState.ascending) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith(comparator.reversed())
        }
    }
}
