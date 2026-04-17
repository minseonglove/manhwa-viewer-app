package com.manhwaviewer.presentation.screen.filemoveselect

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.usecase.CreateFolderUseCase
import com.manhwaviewer.domain.usecase.MoveFilesUseCase
import com.manhwaviewer.domain.usecase.ScanLocalFilesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.filebrowser.NavigationDirection
import com.manhwaviewer.presentation.util.buildPathSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class FileMoveSelectViewModel(
    private val scanLocalFilesUseCase: ScanLocalFilesUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val moveFilesUseCase: MoveFilesUseCase,
    private val moveRequestHolder: MoveRequestHolder,
    private val defaultScanPath: String,
    private val vaultPath: String,
) : MviViewModel<FileMoveSelectState, FileMoveSelectIntent, FileMoveSelectSideEffect>(
    FileMoveSelectState(),
) {

    init {
        val sourcePaths = moveRequestHolder.sourcePaths
        val startPath = moveRequestHolder.startPath
        updateState {
            copy(sourcePaths = sourcePaths, startPath = startPath)
        }
        navigateToFolder(startPath)
    }

    override fun handleIntent(intent: FileMoveSelectIntent) {
        when (intent) {
            is FileMoveSelectIntent.NavigateToFolder -> navigateToFolder(intent.path)
            FileMoveSelectIntent.GoBack -> goBack()
            is FileMoveSelectIntent.GoToSegment -> goToSegment(intent.index)
            FileMoveSelectIntent.ConfirmMove -> confirmMove()
            FileMoveSelectIntent.ShowCreateFolderDialog -> {
                val defaultName = generateDefaultFolderName()
                updateState { copy(showCreateFolderDialog = true, defaultFolderName = defaultName) }
            }
            FileMoveSelectIntent.DismissCreateFolderDialog -> updateState {
                copy(showCreateFolderDialog = false)
            }
            is FileMoveSelectIntent.ConfirmCreateFolder -> confirmCreateFolder(intent.folderName)
        }
    }

    private fun navigateToFolder(
        path: String,
        direction: NavigationDirection = NavigationDirection.FORWARD,
    ) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, currentPath = path, navigationDirection = direction) }
            try {
                val sourcePaths = currentState.sourcePaths.toSet()
                val folders = withContext(com.manhwaviewer.presentation.util.ioDispatcher) {
                    scanLocalFilesUseCase(path)
                        .filter { it.type == ArchiveType.FOLDER }
                        .filter { it.filePath !in sourcePaths }
                        .sortedBy { com.manhwaviewer.domain.util.naturalSortKey(it.fileName) }
                }
                updateState {
                    copy(
                        folders = folders,
                        pathSegments = buildPathSegments(path).map { it.first },
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                updateState { copy(folders = emptyList(), isLoading = false) }
            }
        }
    }

    private fun goBack() {
        if (currentState.currentPath == defaultScanPath) {
            sendSideEffect(FileMoveSelectSideEffect.NavigateBack)
        } else {
            val segments = buildPathSegments(currentState.currentPath)
            if (segments.size > 1) {
                val parentPath = segments[segments.size - 2].second
                navigateToFolder(parentPath, NavigationDirection.BACKWARD)
            } else {
                sendSideEffect(FileMoveSelectSideEffect.NavigateBack)
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

    private fun confirmMove() {
        if (!currentState.isMoveEnabled) return
        updateState { copy(isMoving = true) }
        viewModelScope.launch {
            val result = withContext(com.manhwaviewer.presentation.util.ioDispatcher) {
                moveFilesUseCase(currentState.sourcePaths, currentState.currentPath)
            }
            result.onSuccess {
                moveRequestHolder.clear()
                sendSideEffect(FileMoveSelectSideEffect.MoveCompleted)
            }
            result.onFailure { error ->
                updateState { copy(isMoving = false) }
                sendSideEffect(FileMoveSelectSideEffect.MoveError(error.message ?: "이동 실패"))
            }
        }
    }

    private fun confirmCreateFolder(folderName: String) {
        val parentPath = currentState.currentPath
        viewModelScope.launch {
            val result = createFolderUseCase(parentPath, folderName)
            result.onSuccess { newPath ->
                val now = kotlinx.datetime.Instant.fromEpochMilliseconds(
                    kotlin.time.Clock.System.now().toEpochMilliseconds(),
                )
                val newFolder = ArchiveFile(
                    filePath = newPath,
                    fileName = folderName,
                    type = ArchiveType.FOLDER,
                    addedAt = now,
                )
                val updatedFolders = (currentState.folders + newFolder)
                    .sortedBy { com.manhwaviewer.domain.util.naturalSortKey(it.fileName) }
                updateState {
                    copy(
                        folders = updatedFolders,
                        showCreateFolderDialog = false,
                    )
                }
            }
        }
    }

    private fun generateDefaultFolderName(): String {
        val existingNames = currentState.folders.map { it.fileName }.toSet()
        val base = "새 폴더"
        if (base !in existingNames) return base
        var i = 1
        while ("$base($i)" in existingNames) i++
        return "$base($i)"
    }

    private fun buildPathSegments(path: String): List<Pair<String, String>> =
        buildPathSegments(path, defaultScanPath, vaultPath)
}
