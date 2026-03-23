package com.manhwaviewer.presentation.screen.viewer

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val getArchiveByIdUseCase: GetArchiveByIdUseCase,
    private val updateLastReadIndexUseCase: UpdateLastReadIndexUseCase,
    getViewerPreferenceUseCase: GetViewerPreferenceUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val getImageBytesUseCase: GetImageBytesUseCase,
) : MviViewModel<ViewerState, ViewerIntent, ViewerSideEffect>(ViewerState()) {

    init {
        viewModelScope.launch {
            getViewerPreferenceUseCase().collect { pref ->
                updateState { copy(preference = pref) }
            }
        }
    }

    override fun handleIntent(intent: ViewerIntent) {
        when (intent) {
            is ViewerIntent.LoadArchive -> loadArchive(intent.archiveId, intent.startPage)
            is ViewerIntent.SetPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(currentPage = intent.page.coerceIn(0, totalPages - 1))
                }
            }
            is ViewerIntent.NextPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(currentPage = (currentPage + 1).coerceIn(0, totalPages - 1))
                }
            }
            is ViewerIntent.PreviousPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(currentPage = (currentPage - 1).coerceIn(0, totalPages - 1))
                }
            }
            is ViewerIntent.ToggleControls -> updateState {
                copy(showControls = !showControls)
            }
            is ViewerIntent.SaveProgress -> saveProgress()
        }
    }

    private fun loadArchive(archiveId: Long, startPage: Int = 0) {
        viewModelScope.launch {
            val archive = getArchiveByIdUseCase(archiveId) ?: run {
                updateState { copy(isLoading = false) }
                return@launch
            }
            val entries = getImageEntriesUseCase(archive.filePath)
            val page = if (startPage > 0) startPage else archive.lastReadIndex
            updateState {
                copy(
                    archive = archive,
                    filePath = archive.filePath,
                    imageEntries = entries,
                    currentPage = page.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
                    totalPages = entries.size,
                    isLoading = false,
                )
            }
        }
    }

    suspend fun getImageBytes(page: Int): ByteArray? {
        val state = currentState
        val entryName = state.imageEntries.getOrNull(page) ?: return null
        return getImageBytesUseCase(state.filePath, entryName)
    }

    private fun saveProgress() {
        viewModelScope.launch {
            val state = currentState
            val archive = state.archive ?: return@launch
            updateLastReadIndexUseCase(archive.id, state.currentPage)
        }
    }
}
