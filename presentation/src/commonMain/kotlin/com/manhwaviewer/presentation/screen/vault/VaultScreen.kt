package com.manhwaviewer.presentation.screen.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.screen.common.ActionBarButton
import com.manhwaviewer.presentation.screen.common.BaseTopBar
import com.manhwaviewer.presentation.screen.common.DeleteConfirmDialog
import com.manhwaviewer.presentation.screen.common.RestoreConfirmDialog
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.RenameDialog
import com.manhwaviewer.presentation.screen.common.SelectAllCheckbox
import com.manhwaviewer.presentation.screen.common.SelectionCheckbox
import com.manhwaviewer.presentation.screen.common.SelectionLeadingContent
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_selection_cancel
import manhwa_viewer.presentation.generated.resources.home_shortcut_vault
import manhwa_viewer.presentation.generated.resources.file_selection_count
import manhwa_viewer.presentation.generated.resources.file_selection_delete
import manhwa_viewer.presentation.generated.resources.file_selection_move
import manhwa_viewer.presentation.generated.resources.file_selection_rename
import manhwa_viewer.presentation.generated.resources.ic_delete
import manhwa_viewer.presentation.generated.resources.ic_folder_move
import manhwa_viewer.presentation.generated.resources.ic_lock
import manhwa_viewer.presentation.generated.resources.ic_name
import manhwa_viewer.presentation.generated.resources.ic_repeat
import manhwa_viewer.presentation.generated.resources.vault_empty
import manhwa_viewer.presentation.generated.resources.vault_info_description
import manhwa_viewer.presentation.generated.resources.vault_selection_restore
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VaultScreen(
    onNavigateToViewer: (archiveId: Long, startPage: Int, sortByDate: Boolean, sortAscending: Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToMoveSelect: () -> Unit = {},
    moveCompleted: Boolean = false,
    onMoveCompletedConsumed: () -> Unit = {},
    viewModel: VaultViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val coverModels by viewModel.coverModels.collectAsState()
    val hazeState = rememberHazeState()

    LaunchedEffect(moveCompleted) {
        if (moveCompleted) {
            viewModel.refresh()
            onMoveCompletedConsumed()
        }
    }

    BackHandler(enabled = state.isSelectionMode) {
        viewModel.onIntent(VaultIntent.ExitSelectionMode)
    }

    BackHandler(enabled = !state.isSelectionMode) {
        viewModel.onIntent(VaultIntent.GoBack)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is VaultSideEffect.NavigateToViewer -> onNavigateToViewer(
                    effect.archiveId, effect.startPage, effect.sortByDate, effect.sortAscending,
                )
                VaultSideEffect.NavigateBack -> onBack()
                VaultSideEffect.NavigateToMoveSelect -> onNavigateToMoveSelect()
            }
        }
    }

    Scaffold(
        topBar = {
            BaseTopBar(
                title = if (state.isSelectionMode) {
                    stringResource(Res.string.file_selection_count, state.selectedFileKeys.size)
                } else {
                    stringResource(Res.string.home_shortcut_vault)
                },
                onBack = if (state.isSelectionMode) null else ({ viewModel.onIntent(VaultIntent.GoBack) }),
                pathSegments = state.pathSegments,
                onSegmentClick = { viewModel.onIntent(VaultIntent.GoToSegment(it)) },
                onHomeClick = onNavigateToHome,
                sortState = if (state.isSelectionMode) null else state.sortState,
                onSortFieldChange = { viewModel.onIntent(VaultIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(VaultIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(VaultIntent.ToggleViewMode) },
                leadingContent = if (state.isSelectionMode) {
                    {
                        SelectAllCheckbox(
                            isAllSelected = state.selectedFileKeys.size == state.archiveFiles.size && state.archiveFiles.isNotEmpty(),
                            onClick = { viewModel.onIntent(VaultIntent.ToggleSelectAll) },
                        )
                    }
                } else {
                    null
                },
                trailingActions = if (state.isSelectionMode) {
                    {
                        TextButton(onClick = { viewModel.onIntent(VaultIntent.ExitSelectionMode) }) {
                            Text(
                                text = stringResource(Res.string.file_selection_cancel),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MangaLensColors.blueHi,
                            )
                        }
                    }
                } else {
                    null
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .hazeSource(hazeState),
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val isLandscape = maxWidth > maxHeight
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.archiveFiles.isEmpty() -> {
                        Text(
                            text = stringResource(Res.string.vault_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        val isForward = state.navigationDirection == NavigationDirection.FORWARD
                        AnimatedContent(
                            targetState = state.currentPath,
                            transitionSpec = {
                                if (isForward) {
                                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                                } else {
                                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) { _ ->
                            val isInsideArchive = state.insideArchivePath != null
                            val isAtRoot = state.pathSegments.size <= 1
                            fun coverModelFor(file: ArchiveFile) =
                                if (isInsideArchive) coverModels["${file.filePath}/${file.fileName}"]
                                else coverModels[file.filePath]

                            fun fileKey(file: ArchiveFile) = "${file.filePath}/${file.fileName}"

                            if (state.sortState.viewMode == ListViewMode.LIST) {
                                val columns = if (isLandscape) 2 else 1
                                if (columns == 1) {
                                    LazyColumn(
                                        contentPadding = PaddingValues(16.dp),
                                    ) {
                                        itemsIndexed(state.archiveFiles, key = { _, file -> "${file.filePath}/${file.fileName}" }) { index, file ->
                                            VaultFileItem(
                                                file = file,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = fileKey(file) in state.selectedFileKeys,
                                                showDivider = index < state.archiveFiles.lastIndex,
                                                onClick = {
                                                    if (state.isSelectionMode) {
                                                        viewModel.onIntent(VaultIntent.ToggleSelection(file))
                                                    } else {
                                                        handleFileClick(file, state, viewModel)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!state.isSelectionMode) {
                                                        viewModel.onIntent(VaultIntent.EnterSelectionMode(file))
                                                    }
                                                },
                                                coverModel = coverModelFor(file),
                                                insideArchive = isInsideArchive,
                                            )
                                        }
                                        if (isAtRoot && !state.isSelectionMode) {
                                            item(key = "vault_info_banner") {
                                                VaultInfoBanner(modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(state.archiveFiles, key = { _, file -> "${file.filePath}/${file.fileName}" }) { index, file ->
                                            VaultFileItem(
                                                file = file,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = fileKey(file) in state.selectedFileKeys,
                                                showDivider = index < state.archiveFiles.lastIndex,
                                                onClick = {
                                                    if (state.isSelectionMode) {
                                                        viewModel.onIntent(VaultIntent.ToggleSelection(file))
                                                    } else {
                                                        handleFileClick(file, state, viewModel)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!state.isSelectionMode) {
                                                        viewModel.onIntent(VaultIntent.EnterSelectionMode(file))
                                                    }
                                                },
                                                coverModel = coverModelFor(file),
                                                insideArchive = isInsideArchive,
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 96.dp),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.archiveFiles, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                        FileGridItem(
                                            archive = file,
                                            onClick = {
                                                if (state.isSelectionMode) {
                                                    viewModel.onIntent(VaultIntent.ToggleSelection(file))
                                                } else {
                                                    handleFileClick(file, state, viewModel)
                                                }
                                            },
                                            onLongClick = {
                                                if (!state.isSelectionMode) {
                                                    viewModel.onIntent(VaultIntent.EnterSelectionMode(file))
                                                }
                                            },
                                            coverModel = coverModelFor(file),
                                            isSelectionMode = state.isSelectionMode,
                                            isSelected = fileKey(file) in state.selectedFileKeys,
                                            showLockBadge = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 선택 모드 하단 액션 바
            AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + expandVertically(expandFrom = Alignment.Bottom),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                VaultSelectionActionBar(
                    selectedCount = state.selectedFileKeys.size,
                    onMove = { viewModel.onIntent(VaultIntent.MoveSelected) },
                    onRestore = { viewModel.onIntent(VaultIntent.RestoreSelected) },
                    onDelete = { viewModel.onIntent(VaultIntent.DeleteSelected) },
                    onRename = { viewModel.onIntent(VaultIntent.RenameSelected) },
                )
            }
        }
    }

    // 이름 변경 다이얼로그
    val renameTarget = state.renameTarget
    if (state.showRenameDialog && renameTarget != null) {
        RenameDialog(
            initialName = renameTarget.fileName,
            siblingNames = state.archiveFiles
                .filter { it.fileName != renameTarget.fileName }
                .map { it.fileName }
                .toSet(),
            hazeState = hazeState,
            onConfirm = { newName -> viewModel.onIntent(VaultIntent.ConfirmRename(newName)) },
            onDismiss = { viewModel.onIntent(VaultIntent.DismissRenameDialog) },
        )
    }

    // 삭제 확인 다이얼로그
    if (state.showDeleteDialog && state.deleteTargets.isNotEmpty()) {
        DeleteConfirmDialog(
            count = state.deleteTargets.size,
            firstName = state.deleteTargets.firstOrNull()?.fileName.orEmpty(),
            isFolder = state.deleteTargets.firstOrNull()?.type == ArchiveType.FOLDER,
            hazeState = hazeState,
            onConfirm = { viewModel.onIntent(VaultIntent.ConfirmDelete) },
            onDismiss = { viewModel.onIntent(VaultIntent.DismissDeleteDialog) },
        )
    }

    // 복원 확인 다이얼로그
    if (state.showRestoreDialog && state.restoreTargets.isNotEmpty()) {
        RestoreConfirmDialog(
            count = state.restoreTargets.size,
            firstName = state.restoreTargets.firstOrNull()?.fileName.orEmpty(),
            isFolder = state.restoreTargets.firstOrNull()?.type == ArchiveType.FOLDER,
            hazeState = hazeState,
            onConfirm = { viewModel.onIntent(VaultIntent.ConfirmRestore) },
            onDismiss = { viewModel.onIntent(VaultIntent.DismissRestoreDialog) },
        )
    }
}

private fun handleFileClick(
    file: ArchiveFile,
    state: VaultState,
    viewModel: VaultViewModel,
) {
    when {
        file.type == ArchiveType.FOLDER -> viewModel.onIntent(VaultIntent.NavigateToFolder(file.filePath))
        state.insideArchivePath != null -> viewModel.onIntent(VaultIntent.OpenArchiveEntry(file, file.lastReadIndex))
        file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(VaultIntent.NavigateToFolder(file.filePath))
        else -> viewModel.onIntent(VaultIntent.OpenFile(file))
    }
}

// ── 보관함 파일 아이템 (선택 모드 지원) ─────────────────────────────

@Composable
private fun VaultFileItem(
    file: ArchiveFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showDivider: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    coverModel: com.manhwaviewer.presentation.screen.viewer.ViewerPageModel?,
    insideArchive: Boolean,
) {
    FileItem(
        archive = file,
        onClick = onClick,
        onLongClick = onLongClick,
        coverModel = coverModel,
        insideArchive = insideArchive,
        showLockBadge = false,
        showDivider = showDivider,
        leadingContent = {
            SelectionLeadingContent(
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
            )
        },
        trailingContent = if (isSelectionMode) {
            {}
        } else {
            null
        },
        modifier = Modifier.background(
            if (isSelected) MangaLensColors.blue.copy(alpha = 0.08f) else Color.Transparent,
        ),
    )
}

// ── 보관함 선택 모드 하단 액션 바 ──────────────────────────────────

@Composable
private fun VaultSelectionActionBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val hasSelection = selectedCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(color = MangaLensColors.separator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            ActionBarButton(
                icon = { tint ->
                    Icon(
                        painter = painterResource(Res.drawable.ic_folder_move),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = stringResource(Res.string.file_selection_move),
                color = if (hasSelection) MangaLensColors.blueHi else MangaLensColors.textTertiary,
                enabled = hasSelection,
                onClick = onMove,
            )
            ActionBarButton(
                icon = { tint ->
                    Icon(
                        painter = painterResource(Res.drawable.ic_repeat),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = stringResource(Res.string.vault_selection_restore),
                color = if (hasSelection) MangaLensColors.green else MangaLensColors.textTertiary,
                enabled = hasSelection,
                onClick = onRestore,
            )
            ActionBarButton(
                icon = { tint ->
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = stringResource(Res.string.file_selection_delete),
                color = if (hasSelection) MangaLensColors.red else MangaLensColors.textTertiary,
                enabled = hasSelection,
                onClick = onDelete,
            )
            if (selectedCount <= 1) {
                ActionBarButton(
                    icon = { tint ->
                        Icon(
                            painter = painterResource(Res.drawable.ic_name),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = stringResource(Res.string.file_selection_rename),
                    color = if (hasSelection) MangaLensColors.onSurface else MangaLensColors.textTertiary,
                    enabled = hasSelection,
                    onClick = onRename,
                )
            }
        }
    }
}


// ── 보관함 안내 배너 ──────────────────────────────────────────────

@Composable
private fun VaultInfoBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MangaLensColors.tilePurple.copy(alpha = 0.08f),
        ),
        border = BorderStroke(0.5.dp, MangaLensColors.tilePurple.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = null,
                    tint = MangaLensColors.tilePurple,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(Res.string.home_shortcut_vault),
                    style = MaterialTheme.typography.labelMedium,
                    color = MangaLensColors.tilePurple,
                )
            }
            Text(
                text = stringResource(Res.string.vault_info_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
