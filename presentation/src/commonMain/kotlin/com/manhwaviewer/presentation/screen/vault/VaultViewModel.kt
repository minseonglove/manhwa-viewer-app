package com.manhwaviewer.presentation.screen.vault

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.DeleteVaultFilesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.RenameFileUseCase
import com.manhwaviewer.domain.usecase.RestoreFromVaultUseCase
import com.manhwaviewer.domain.usecase.ScanArchiveEntriesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.screen.filemoveselect.MoveRequestHolder
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
    private val deleteVaultFilesUseCase: DeleteVaultFilesUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    private val restoreFromVaultUseCase: RestoreFromVaultUseCase,
    private val moveRequestHolder: MoveRequestHolder,
    private val vaultPath: String,
    private val ioDispatcher: CoroutineContext = com.manhwaviewer.presentation.util.ioDispatcher,
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
            is VaultIntent.EnterSelectionMode -> enterSelectionMode(intent.file)
            is VaultIntent.ToggleSelection -> toggleSelection(intent.file)
            VaultIntent.ToggleSelectAll -> toggleSelectAll()
            VaultIntent.ExitSelectionMode -> updateState {
                copy(isSelectionMode = false, selectedFileKeys = emptySet())
            }
            VaultIntent.MoveSelected -> moveSelected()
            VaultIntent.RestoreSelected -> showRestoreDialog()
            VaultIntent.ConfirmRestore -> confirmRestore()
            VaultIntent.DismissRestoreDialog -> updateState {
                copy(showRestoreDialog = false, restoreTargets = emptyList())
            }
            VaultIntent.DeleteSelected -> showDeleteDialog()
            VaultIntent.RenameSelected -> showRenameDialog()
            is VaultIntent.ConfirmRename -> confirmRename(intent.newName)
            VaultIntent.DismissRenameDialog -> updateState {
                copy(showRenameDialog = false, renameTarget = null)
            }
            VaultIntent.ConfirmDelete -> confirmDelete()
            VaultIntent.DismissDeleteDialog -> updateState {
                copy(showDeleteDialog = false, deleteTargets = emptyList())
            }
        }
    }

    private fun navigateToFolder(path: String, direction: NavigationDirection = NavigationDirection.FORWARD) {
        collectJob?.cancel()
        coverLoadJob?.cancel()
        _coverModels.value = emptyMap()
        collectJob = viewModelScope.launch {
            updateState {
                copy(
                    isLoading = true,
                    currentPath = path,
                    navigationDirection = direction,
                    isSelectionMode = false,
                    selectedFileKeys = emptySet(),
                )
            }
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

    private fun buildPathSegments(path: String): List<Pair<String, String>> =
        com.manhwaviewer.presentation.util.buildPathSegments(path, vaultPath, vaultPath)

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

    fun refresh() {
        navigateToFolder(currentState.currentPath)
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

    private fun toggleSelectAll() {
        updateState {
            val allKeys = archiveFiles.map { fileKey(it) }.toSet()
            if (selectedFileKeys == allKeys) {
                copy(selectedFileKeys = emptySet())
            } else {
                copy(selectedFileKeys = allKeys)
            }
        }
    }

    private fun moveSelected() {
        val paths = currentState.archiveFiles
            .filter { fileKey(it) in currentState.selectedFileKeys }
            .map { it.filePath }
        if (paths.isNotEmpty()) {
            moveRequestHolder.sourcePaths = paths
            moveRequestHolder.startPath = currentState.currentPath
            sendSideEffect(VaultSideEffect.NavigateToMoveSelect)
        }
    }

    private fun showDeleteDialog() {
        val selectedKeys = currentState.selectedFileKeys
        if (selectedKeys.isEmpty()) return
        val targets = currentState.archiveFiles.filter { fileKey(it) in selectedKeys }
        if (targets.isEmpty()) return
        updateState { copy(showDeleteDialog = true, deleteTargets = targets) }
    }

    private fun confirmDelete() {
        val targets = currentState.deleteTargets
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val paths = targets.map { it.filePath }
            val result = deleteVaultFilesUseCase(paths, vaultPath)
            val successCount = result.getOrDefault(0)
            if (successCount > 0) {
                val deletedKeys = targets.take(successCount).map { fileKey(it) }.toSet()
                _coverModels.value = _coverModels.value.filterKeys { it !in deletedKeys }
                updateState {
                    copy(
                        archiveFiles = archiveFiles.filter { fileKey(it) !in deletedKeys },
                        showDeleteDialog = false,
                        deleteTargets = emptyList(),
                        isSelectionMode = false,
                        selectedFileKeys = emptySet(),
                    )
                }
            } else {
                updateState { copy(showDeleteDialog = false, deleteTargets = emptyList()) }
            }
        }
    }

    private fun showRestoreDialog() {
        val selectedKeys = currentState.selectedFileKeys
        if (selectedKeys.isEmpty()) return
        val targets = currentState.archiveFiles.filter { fileKey(it) in selectedKeys }
        if (targets.isEmpty()) return
        updateState { copy(showRestoreDialog = true, restoreTargets = targets) }
    }

    private fun confirmRestore() {
        val targets = currentState.restoreTargets
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val paths = targets.map { it.filePath }
            val result = restoreFromVaultUseCase(paths, vaultPath)
            val successCount = result.getOrDefault(0)
            if (successCount > 0) {
                val restoredKeys = targets.take(successCount).map { fileKey(it) }.toSet()
                _coverModels.value = _coverModels.value.filterKeys { it !in restoredKeys }
                updateState {
                    copy(
                        archiveFiles = archiveFiles.filter { fileKey(it) !in restoredKeys },
                        showRestoreDialog = false,
                        restoreTargets = emptyList(),
                        isSelectionMode = false,
                        selectedFileKeys = emptySet(),
                    )
                }
            } else {
                updateState { copy(showRestoreDialog = false, restoreTargets = emptyList()) }
            }
        }
    }

    private fun showRenameDialog() {
        if (currentState.selectedFileKeys.size != 1) return
        val selectedKey = currentState.selectedFileKeys.first()
        val target = currentState.archiveFiles.firstOrNull { fileKey(it) == selectedKey } ?: return
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

                _coverModels.value = _coverModels.value.toMutableMap().apply {
                    remove(oldKey)?.let { put(newKey, it) }
                }
                updateState {
                    val updatedFiles = archiveFiles.map { if (fileKey(it) == oldKey) renamedFile else it }
                    copy(
                        archiveFiles = updatedFiles,
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
        val secondaryComparator = when (currentState.sortState.field) {
            SortField.NAME -> compareBy<ArchiveFile> { it.fileName.lowercase() }
            SortField.DATE -> compareBy<ArchiveFile> { it.addedAt }
        }
        val comparator = compareBy<ArchiveFile> { typeOrder(it.type) }
            .then(if (currentState.sortState.ascending) secondaryComparator else secondaryComparator.reversed())
        return files.sortedWith(comparator)
    }
}
