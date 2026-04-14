package com.manhwaviewer.presentation.screen.vault

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.VaultFile
import com.manhwaviewer.domain.usecase.GetVaultDirectoryFilesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VaultViewModel(
    private val getVaultDirectoryFilesUseCase: GetVaultDirectoryFilesUseCase,
) : MviViewModel<VaultState, VaultIntent, VaultSideEffect>(VaultState()) {

    private var collectJob: Job? = null

    init {
        loadFiles("")
    }

    private fun loadFiles(path: String) {
        collectJob?.cancel()
        val prefix = if (path.isEmpty()) "" else "$path/"
        collectJob = viewModelScope.launch {
            getVaultDirectoryFilesUseCase(prefix).collect { files ->
                val sorted = sortFiles(files)
                updateState { copy(files = sorted, currentPath = path, isLoading = false) }
            }
        }
    }

    override fun handleIntent(intent: VaultIntent) {
        when (intent) {
            is VaultIntent.OpenFile -> {
                // TODO: vault 파일 열기 구현 (vault root path 확정 후)
            }
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

    private fun resortFiles() {
        updateState { copy(files = sortFiles(files)) }
    }

    private fun sortFiles(files: List<VaultFile>): List<VaultFile> {
        val comparator = when (currentState.sortState.field) {
            SortField.NAME -> compareBy<VaultFile> { it.relativePath.substringAfterLast('/').lowercase() }
            SortField.DATE -> compareBy { it.addedAt }
        }
        return if (currentState.sortState.ascending) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith(comparator.reversed())
        }
    }
}
