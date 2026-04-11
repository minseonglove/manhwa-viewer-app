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
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
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
    private var coverLoadJob: Job? = null

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
        coverLoadJob?.cancel()
        viewModelScope.launch {
            updateState { copy(isLoading = true, currentPath = path, navigationDirection = direction, coverModels = emptyMap()) }
            try {
                val ext = path.substringAfterLast('.').lowercase()
                val isArchive = ext in archiveExtensions
                val files = withContext(ioDispatcher) {
                    if (isArchive) scanArchiveEntriesUseCase(path)
                    else scanLocalFilesUseCase(path)
                }
                val sorted = sortFiles(files)
                updateState {
                    copy(
                        files = sorted,
                        pathSegments = buildPathSegments(path).map { it.first },
                        insideArchivePath = if (isArchive) path else null,
                        isLoading = false,
                    )
                }
                loadCoverModels(sorted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                updateState { copy(files = emptyList(), isLoading = false) }
            }
        }
    }

    private fun loadCoverModels(files: List<ArchiveFile>) {
        val archiveFiles = files.filter { it.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) }
        if (archiveFiles.isEmpty()) return
        coverLoadJob = viewModelScope.launch {
            supervisorScope {
                val results = archiveFiles.map { archive ->
                    async(ioDispatcher) {
                        try {
                            val entries = getImageEntriesUseCase(archive.filePath)
                            if (entries.isNotEmpty()) {
                                archive.filePath to ViewerPageModel(
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
                updateState { copy(coverModels = results) }
            }
        }
    }

    private fun openFile(archive: ArchiveFile) {
        viewModelScope.launch {
            val sortByDate = currentState.sortState.field == SortField.DATE
            val sortAscending = currentState.sortState.ascending
            val archiveForDb = if (archive.type == ArchiveType.IMAGE) {
                val parentPath = archive.filePath.substringBeforeLast('/')
                val entries = withContext(ioDispatcher) {
                    getImageEntriesUseCase(parentPath, sortByDate, sortAscending)
                }
                val pageIndex = entries.indexOf(archive.fileName).coerceAtLeast(0)
                archive.copy(
                    filePath = parentPath,
                    fileName = parentPath.substringAfterLast('/'),
                    type = ArchiveType.FOLDER,
                    totalPages = entries.size,
                    lastReadIndex = pageIndex,
                )
            } else {
                archive
            }
            val id = upsertArchiveUseCase(archiveForDb)
            sendSideEffect(FileBrowserSideEffect.NavigateToViewer(id, archiveForDb.lastReadIndex, sortByDate, sortAscending))
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
            val archivePath = currentState.insideArchivePath ?: return@launch
            val sortByDate = currentState.sortState.field == SortField.DATE
            val sortAscending = currentState.sortState.ascending
            val entries = withContext(ioDispatcher) {
                getImageEntriesUseCase(archivePath, sortByDate, sortAscending)
            }
            val actualIndex = entries.indexOf(archive.fileName).coerceAtLeast(0)

            val archiveForDb = archive.copy(
                fileName = archivePath.substringAfterLast('/'),
                lastReadIndex = actualIndex,
            )
            val id = upsertArchiveUseCase(archiveForDb)
            sendSideEffect(FileBrowserSideEffect.NavigateToViewer(id, actualIndex, sortByDate, sortAscending))
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
        val typeOrder = { type: ArchiveType ->
            when (type) {
                ArchiveType.FOLDER -> 0
                ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR -> 1
                ArchiveType.IMAGE -> 2
            }
        }
        val secondaryComparator = when (field) {
            SortField.NAME -> compareBy<ArchiveFile> { com.manhwaviewer.domain.util.naturalSortKey(it.fileName) }
            SortField.DATE -> compareBy { it.addedAt }
        }
        val comparator = compareBy<ArchiveFile> { typeOrder(it.type) }
            .then(if (ascending) secondaryComparator else secondaryComparator.reversed())
        return files.sortedWith(comparator)
    }

}
