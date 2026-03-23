package com.manhwaviewer.presentation.screen.vault

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.usecase.GetVaultFilesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import kotlinx.coroutines.launch

class VaultViewModel(
    getVaultFilesUseCase: GetVaultFilesUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
) : MviViewModel<VaultState, VaultIntent, VaultSideEffect>(VaultState()) {

    init {
        viewModelScope.launch {
            getVaultFilesUseCase().collect { files ->
                updateState { copy(files = sortFiles(files), isLoading = false) }
            }
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
