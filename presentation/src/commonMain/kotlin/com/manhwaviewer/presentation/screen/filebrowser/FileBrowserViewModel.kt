package com.manhwaviewer.presentation.screen.filebrowser

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.DeleteFileUseCase
import com.manhwaviewer.domain.usecase.RenameFileUseCase
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
    private val renameFileUseCase: RenameFileUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
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
            is FileBrowserIntent.EnterSelectionMode -> enterSelectionMode(intent.file)
            is FileBrowserIntent.ToggleSelection -> toggleSelection(intent.file)
            FileBrowserIntent.ToggleSelectAll -> toggleSelectAll()
            FileBrowserIntent.ExitSelectionMode -> updateState {
                copy(isSelectionMode = false, selectedFileKeys = emptySet())
            }
            FileBrowserIntent.MoveSelected -> { /* TODO: 선택 항목 이동 */ }
            FileBrowserIntent.VaultSelected -> { /* TODO: 선택 항목 보관함 이동 */ }
            FileBrowserIntent.DeleteSelected -> showDeleteDialog()
            FileBrowserIntent.RenameSelected -> showRenameDialog()
            is FileBrowserIntent.ConfirmRename -> confirmRename(intent.newName)
            FileBrowserIntent.DismissRenameDialog -> updateState {
                copy(showRenameDialog = false, renameTarget = null)
            }
            FileBrowserIntent.ConfirmDelete -> confirmDelete()
            FileBrowserIntent.DismissDeleteDialog -> updateState {
                copy(showDeleteDialog = false, deleteTargets = emptyList())
            }
        }
    }

    private fun fileKey(file: ArchiveFile): String = "${file.filePath}/${file.fileName}"

    private fun enterSelectionMode(file: ArchiveFile) {
        updateState {
            copy(
                isSelectionMode = true,
                selectedFileKeys = setOf(fileKey(file)),
            )
        }
    }

    private fun toggleSelection(file: ArchiveFile) {
        updateState {
            val key = fileKey(file)
            val newSelected = if (key in selectedFileKeys) {
                selectedFileKeys - key
            } else {
                selectedFileKeys + key
            }
            copy(selectedFileKeys = newSelected)
        }
    }

    private fun showDeleteDialog() {
        val selectedKeys = currentState.selectedFileKeys
        if (selectedKeys.isEmpty()) return
        val targets = currentState.files.filter { fileKey(it) in selectedKeys }
        if (targets.isEmpty()) return
        updateState { copy(showDeleteDialog = true, deleteTargets = targets) }
    }

    private fun confirmDelete() {
        val targets = currentState.deleteTargets
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val deletedKeys = supervisorScope {
                targets.map { target ->
                    async(ioDispatcher) {
                        val result = deleteFileUseCase(target.filePath)
                        if (result.isSuccess) fileKey(target) else null
                    }
                }.mapNotNull { it.await() }.toSet()
            }
            updateState {
                copy(
                    files = files.filter { fileKey(it) !in deletedKeys },
                    coverModels = coverModels.filterKeys { it !in deletedKeys },
                    showDeleteDialog = false,
                    deleteTargets = emptyList(),
                    isSelectionMode = false,
                    selectedFileKeys = emptySet(),
                )
            }
        }
    }

    private fun showRenameDialog() {
        if (currentState.selectedFileKeys.size != 1) return
        val selectedKey = currentState.selectedFileKeys.first()
        val target = currentState.files.firstOrNull { fileKey(it) == selectedKey } ?: return
        updateState { copy(showRenameDialog = true, renameTarget = target) }
    }

    private fun confirmRename(newName: String) {
        val target = currentState.renameTarget ?: return
        viewModelScope.launch {
            val result = renameFileUseCase(target.filePath, newName)
            result.onSuccess { newPath ->
                val oldKey = fileKey(target)
                val renamedFile = target.copy(filePath = newPath, fileName = newName)
                val newKey = fileKey(renamedFile)

                updateState {
                    val updatedFiles = files.map { if (fileKey(it) == oldKey) renamedFile else it }
                    val updatedCovers = coverModels.toMutableMap().apply {
                        remove(oldKey)?.let { put(newKey, it) }
                    }
                    copy(
                        files = updatedFiles,
                        coverModels = updatedCovers,
                        showRenameDialog = false,
                        renameTarget = null,
                        isSelectionMode = false,
                        selectedFileKeys = emptySet(),
                    )
                }
            }
            result.onFailure {
                // 파일시스템 레벨 실패 — 다이얼로그 유지
            }
        }
    }

    private fun toggleSelectAll() {
        updateState {
            val allKeys = files.map { fileKey(it) }.toSet()
            if (selectedFileKeys == allKeys) {
                copy(selectedFileKeys = emptySet())
            } else {
                copy(selectedFileKeys = allKeys)
            }
        }
    }

    private fun navigateToFolder(path: String, direction: NavigationDirection = NavigationDirection.FORWARD) {
        coverLoadJob?.cancel()
        viewModelScope.launch {
            updateState { copy(isLoading = true, currentPath = path, navigationDirection = direction, coverModels = emptyMap(), isSelectionMode = false, selectedFileKeys = emptySet()) }
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
        val insideArchive = currentState.insideArchivePath
        if (insideArchive != null) {
            // 아카이브 내부 — 각 항목이 이미지 엔트리이므로 직접 ViewerPageModel 생성
            val models = files.associate { file ->
                "${file.filePath}/${file.fileName}" to ViewerPageModel(
                    archivePath = file.filePath,
                    entryNames = listOf(file.fileName),
                )
            }
            updateState { copy(coverModels = models) }
            return
        }
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
                updateState {
                    copy(
                        coverModels = results,
                        files = this.files.map { file ->
                            if (file.filePath in results) file.copy(hasImages = true) else file
                        },
                    )
                }
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
