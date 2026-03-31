package com.manhwaviewer.presentation.screen.filebrowser

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileBrowserViewModel(
    private val scanLocalFilesUseCase: ScanLocalFilesUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
    private val scanArchiveEntriesUseCase: ScanArchiveEntriesUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val defaultScanPath: String,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : MviViewModel<FileBrowserState, FileBrowserIntent, FileBrowserSideEffect>(FileBrowserState()) {

    private val archiveExtensions = setOf("zip", "cbz", "cbr")

    init {
        navigateToFolder(defaultScanPath)
    }

    override fun handleIntent(intent: FileBrowserIntent) {
        when (intent) {
            is FileBrowserIntent.NavigateToFolder -> navigateToFolder(intent.path, NavigationDirection.FORWARD)
            is FileBrowserIntent.OpenFile -> openFile(intent.archive)
            is FileBrowserIntent.OpenArchiveEntry -> openArchiveEntry(intent.archive, intent.pageIndex)
            is FileBrowserIntent.SetSortField -> updateState {
                val newState = sortState.copy(field = intent.field)
                copy(sortState = newState, files = sortFiles(files, newState.field, newState.ascending))
            }
            FileBrowserIntent.ToggleSortDirection -> updateState {
                val newAscending = !sortState.ascending
                val newState = sortState.copy(ascending = newAscending)
                copy(sortState = newState, files = sortFiles(files, newState.field, newState.ascending))
            }
            FileBrowserIntent.ToggleViewMode -> updateState {
                copy(
                    sortState = sortState.copy(
                        viewMode = if (sortState.viewMode == ListViewMode.LIST) ListViewMode.GRID else ListViewMode.LIST,
                    ),
                )
            }
            FileBrowserIntent.GoBack -> goBack()
            FileBrowserIntent.GoHome -> navigateToFolder(defaultScanPath)
            is FileBrowserIntent.GoToSegment -> goToSegment(intent.index)
        }
    }

    private fun navigateToFolder(path: String, direction: NavigationDirection = NavigationDirection.FORWARD) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, currentPath = path, navigationDirection = direction) }
            try {
                val ext = path.substringAfterLast('.').lowercase()
                val isArchive = ext in archiveExtensions
                val files = withContext(ioDispatcher) {
                    if (isArchive) scanArchiveEntriesUseCase(path)
                    else scanLocalFilesUseCase(path)
                }
                updateState {
                    copy(
                        files = sortFiles(files),
                        pathSegments = buildPathSegments(path).map { it.first },
                        insideArchivePath = if (isArchive) path else null,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                updateState { copy(files = emptyList(), isLoading = false) }
            }
        }
    }

    private fun openFile(archive: ArchiveFile) {
        viewModelScope.launch {
            val archiveForDb = if (archive.type == ArchiveType.IMAGE) {
                val parentPath = archive.filePath.substringBeforeLast('/')
                val entries = withContext(ioDispatcher) {
                    getImageEntriesUseCase(parentPath)
                }
                val pageIndex = entries.indexOf(archive.fileName).coerceAtLeast(0)
                archive.copy(
                    filePath = parentPath,
                    type = ArchiveType.FOLDER,
                    totalPages = entries.size,
                    lastReadIndex = pageIndex,
                )
            } else {
                archive
            }
            val id = upsertArchiveUseCase(archiveForDb)
            sendSideEffect(FileBrowserSideEffect.NavigateToViewer(id, archiveForDb.lastReadIndex))
        }
    }

    private fun goBack() {
        val insideArchive = currentState.insideArchivePath
        if (insideArchive != null) {
            val parentPath = insideArchive.substringBeforeLast('/')
            navigateToFolder(parentPath, NavigationDirection.BACKWARD)
            return
        }
        if (currentState.currentPath == defaultScanPath) {
            viewModelScope.launch {
                sendSideEffect(FileBrowserSideEffect.NavigateToHome)
            }
        } else {
            val segments = buildPathSegments(currentState.currentPath)
            if (segments.size > 1) {
                val parentPath = segments[segments.size - 2].second
                navigateToFolder(parentPath, NavigationDirection.BACKWARD)
            }
        }
    }

    private fun openArchiveEntry(archive: ArchiveFile, pageIndex: Int) {
        viewModelScope.launch {
            val archiveForDb = archive.copy(
                fileName = archive.filePath.substringAfterLast('/'),
                lastReadIndex = pageIndex,
            )
            val id = upsertArchiveUseCase(archiveForDb)
            sendSideEffect(FileBrowserSideEffect.NavigateToViewer(id, pageIndex))
        }
    }

    private fun goToSegment(index: Int) {
        val segments = buildPathSegments(currentState.currentPath)
        if (index < segments.size) {
            val direction = if (index < segments.size - 1) NavigationDirection.BACKWARD else NavigationDirection.FORWARD
            navigateToFolder(segments[index].second, direction)
        }
    }

    private fun buildPathSegments(path: String): List<Pair<String, String>> {
        val parts = path.split("/").filter { it.isNotEmpty() }
        val result = mutableListOf<Pair<String, String>>()
        var accumulated = ""
        for (part in parts) {
            accumulated = "$accumulated/$part"
            result.add(part to accumulated)
        }
        return result
    }

    private fun sortFiles(
        files: List<ArchiveFile>,
        field: SortField = currentState.sortState.field,
        ascending: Boolean = currentState.sortState.ascending,
    ): List<ArchiveFile> {
        val comparator = when (field) {
            SortField.NAME -> compareBy<ArchiveFile> { naturalSortKey(it.fileName) }
            SortField.DATE -> compareBy { it.addedAt }
        }
        return if (ascending) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith(comparator.reversed())
        }
    }

    private fun naturalSortKey(name: String): String {
        return name.lowercase().replace(Regex("(\\d+)")) { match ->
            match.value.padStart(20, '0')
        }
    }
}
