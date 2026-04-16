package com.manhwaviewer.presentation.screen.search

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.SearchFilesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val defaultScanPath: String,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : MviViewModel<SearchState, SearchIntent, SearchSideEffect>(SearchState()) {

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    searchJob?.cancel()
                    if (query.isBlank()) {
                        updateState { copy(folders = emptyList(), archives = emptyList(), images = emptyList(), isLoading = false) }
                    } else {
                        updateState { copy(isLoading = true) }
                        searchJob = launch {
                            val result = withContext(ioDispatcher) {
                                searchFilesUseCase(defaultScanPath, query)
                            }
                            updateState {
                                copy(
                                    folders = result.folders,
                                    archives = result.archives,
                                    images = result.images,
                                    isLoading = false,
                                )
                            }
                        }
                    }
                }
        }
    }

    override fun handleIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> {
                updateState { copy(query = intent.query) }
                queryFlow.value = intent.query
            }
            SearchIntent.ClearQuery -> {
                updateState { copy(query = "") }
                queryFlow.value = ""
            }
            is SearchIntent.OpenFile -> openFile(intent.file)
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchFilesUseCase.invalidateCache()
    }

    private fun openFile(file: ArchiveFile) {
        viewModelScope.launch {
            when (file.type) {
                ArchiveType.FOLDER -> {
                    sendSideEffect(SearchSideEffect.NavigateToFileBrowser(file.filePath))
                }
                ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR -> {
                    sendSideEffect(SearchSideEffect.NavigateToFileBrowser(file.filePath))
                }
                ArchiveType.IMAGE -> {
                    val parentPath = file.filePath.substringBeforeLast('/')
                    val entries = withContext(ioDispatcher) {
                        getImageEntriesUseCase(parentPath)
                    }
                    val pageIndex = entries.indexOf(file.fileName).coerceAtLeast(0)
                    val archive = ArchiveFile(
                        filePath = parentPath,
                        fileName = parentPath.substringAfterLast('/'),
                        type = ArchiveType.FOLDER,
                        totalPages = entries.size,
                        lastReadIndex = pageIndex,
                        addedAt = Instant.fromEpochMilliseconds(
                            kotlin.time.Clock.System.now().toEpochMilliseconds(),
                        ),
                        hasImages = true,
                    )
                    val id = upsertArchiveUseCase(archive)
                    sendSideEffect(SearchSideEffect.NavigateToViewer(id, pageIndex))
                }
            }
        }
    }
}
