package com.manhwaviewer.presentation.screen.vault

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class VaultViewModel(
    private val scanLocalFilesUseCase: ScanLocalFilesUseCase,
    private val scanArchiveEntriesUseCase: ScanArchiveEntriesUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
    private val vaultPath: String,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO,
) : MviViewModel<VaultState, VaultIntent, VaultSideEffect>(VaultState()) {

    private val archiveExtensions = setOf("zip", "cbz", "cbr")
    private var collectJob: Job? = null
    private var coverLoadJob: Job? = null
    private val _coverModels = MutableStateFlow<Map<String, ViewerPageModel>>(emptyMap())
    val coverModels: StateFlow<Map<String, ViewerPageModel>> = _coverModels.asStateFlow()

    init {
        navigateToFolder(vaultPath)
    }

    override fun handleIntent(intent: VaultIntent) {
        when (intent) {
            is VaultIntent.OpenFile -> openFile(intent.file)
            is VaultIntent.OpenArchiveEntry -> openArchiveEntry(intent.file, intent.pageIndex)
            is VaultIntent.NavigateToFolder -> navigateToFolder(intent.path, NavigationDirection.FORWARD)
            VaultIntent.GoBack -> goBack()
            is VaultIntent.GoToSegment -> goToSegment(intent.index)
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

    private fun navigateToFolder(path: String, direction: NavigationDirection = NavigationDirection.FORWARD) {
        collectJob?.cancel()
        coverLoadJob?.cancel()
        _coverModels.value = emptyMap()
        collectJob = viewModelScope.launch {
            updateState { copy(isLoading = true, currentPath = path, navigationDirection = direction) }
            try {
                val ext = path.substringAfterLast('.').lowercase()
                val isArchive = ext in archiveExtensions
                val sorted = withContext(ioDispatcher) {
                    val files = if (isArchive) scanArchiveEntriesUseCase(path)
                    else scanLocalFilesUseCase(path)
                    sortArchiveFiles(files)
                }
                updateState {
                    copy(
                        archiveFiles = sorted,
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
                updateState { copy(archiveFiles = emptyList(), isLoading = false) }
            }
        }
    }

    private fun goBack() {
        val insideArchive = currentState.insideArchivePath
        if (insideArchive != null) {
            val parentPath = insideArchive.substringBeforeLast('/')
            navigateToFolder(parentPath, NavigationDirection.BACKWARD)
            return
        }
        if (currentState.currentPath == vaultPath) {
            viewModelScope.launch {
                sendSideEffect(VaultSideEffect.NavigateBack)
            }
        } else {
            val segments = buildPathSegments(currentState.currentPath)
            if (segments.size > 1) {
                val parentPath = segments[segments.size - 2].second
                navigateToFolder(parentPath, NavigationDirection.BACKWARD)
            }
        }
    }

    private fun goToSegment(index: Int) {
        val segments = buildPathSegments(currentState.currentPath)
        if (index < segments.size) {
            val direction = if (index < segments.size - 1) NavigationDirection.BACKWARD else NavigationDirection.FORWARD
            navigateToFolder(segments[index].second, direction)
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
            sendSideEffect(VaultSideEffect.NavigateToViewer(id, archiveForDb.lastReadIndex, sortByDate, sortAscending))
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
            sendSideEffect(VaultSideEffect.NavigateToViewer(id, actualIndex, sortByDate, sortAscending))
        }
    }

    private fun buildPathSegments(path: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>("보관함" to vaultPath)
        if (path.startsWith(vaultPath)) {
            val remaining = path.removePrefix(vaultPath)
            val extraParts = remaining.split("/").filter { it.isNotEmpty() }
            var accumulated = vaultPath
            for (part in extraParts) {
                accumulated = "$accumulated/$part"
                result.add(part to accumulated)
            }
        }
        return result
    }

    private fun loadCoverModels(files: List<ArchiveFile>) {
        val insideArchive = currentState.insideArchivePath
        if (insideArchive != null) {
            val models = files.associate { file ->
                "${file.filePath}/${file.fileName}" to ViewerPageModel(
                    archivePath = file.filePath,
                    entryNames = listOf(file.fileName),
                )
            }
            _coverModels.value = models
            return
        }
        val archiveFiles = files.filter { it.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) }
        if (archiveFiles.isEmpty()) return
        val semaphore = kotlinx.coroutines.sync.Semaphore(3)
        coverLoadJob = viewModelScope.launch {
            supervisorScope {
                archiveFiles.chunked(6).forEach { batch ->
                    val results = batch.map { archive ->
                        async(ioDispatcher) {
                            semaphore.acquire()
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
                            } finally {
                                semaphore.release()
                            }
                        }
                    }
                    results.forEach { deferred ->
                        val pair = try { deferred.await() } catch (_: Exception) { null }
                        if (pair != null) {
                            _coverModels.value = _coverModels.value + pair
                        }
                    }
                }
            }
        }
    }

    private fun resortFiles() {
        updateState { copy(archiveFiles = sortArchiveFiles(archiveFiles)) }
    }

    private fun sortArchiveFiles(files: List<ArchiveFile>): List<ArchiveFile> {
        val typeOrder = { type: ArchiveType ->
            when (type) {
                ArchiveType.FOLDER -> 0
                else -> 1
            }
        }
        val comparator = when (currentState.sortState.field) {
            SortField.NAME -> compareBy<ArchiveFile> { typeOrder(it.type) }
                .thenBy { it.fileName.lowercase() }
            SortField.DATE -> compareBy<ArchiveFile> { typeOrder(it.type) }
                .thenBy { it.addedAt }
        }
        return if (currentState.sortState.ascending) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith(comparator.reversed())
        }
    }
}
