package com.manhwaviewer.presentation.screen.filebrowser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.screen.common.BaseTopBar
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.GlassConfirmDialog
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_browser_empty
import manhwa_viewer.presentation.generated.resources.file_browser_title
import manhwa_viewer.presentation.generated.resources.file_create_folder_confirm
import manhwa_viewer.presentation.generated.resources.file_create_folder_title
import manhwa_viewer.presentation.generated.resources.file_delete_confirm
import manhwa_viewer.presentation.generated.resources.file_delete_message_multiple_highlight
import manhwa_viewer.presentation.generated.resources.file_delete_message_multiple_suffix
import manhwa_viewer.presentation.generated.resources.file_delete_message_single_file_suffix
import manhwa_viewer.presentation.generated.resources.file_delete_message_single_folder_suffix
import manhwa_viewer.presentation.generated.resources.file_delete_message_single_highlight
import manhwa_viewer.presentation.generated.resources.file_delete_title
import manhwa_viewer.presentation.generated.resources.file_delete_warning
import manhwa_viewer.presentation.generated.resources.file_selection_cancel
import manhwa_viewer.presentation.generated.resources.file_selection_count
import manhwa_viewer.presentation.generated.resources.file_selection_delete
import manhwa_viewer.presentation.generated.resources.file_selection_move
import manhwa_viewer.presentation.generated.resources.file_selection_rename
import manhwa_viewer.presentation.generated.resources.file_selection_select_all
import manhwa_viewer.presentation.generated.resources.file_selection_vault
import manhwa_viewer.presentation.generated.resources.ic_check_off
import manhwa_viewer.presentation.generated.resources.ic_check_on
import manhwa_viewer.presentation.generated.resources.ic_close_circle
import manhwa_viewer.presentation.generated.resources.ic_delete
import manhwa_viewer.presentation.generated.resources.ic_folder_move
import manhwa_viewer.presentation.generated.resources.ic_hide
import manhwa_viewer.presentation.generated.resources.ic_lock
import manhwa_viewer.presentation.generated.resources.ic_name
import manhwa_viewer.presentation.generated.resources.ic_plus
import manhwa_viewer.presentation.generated.resources.vault_move_confirm
import manhwa_viewer.presentation.generated.resources.vault_move_info_hidden
import manhwa_viewer.presentation.generated.resources.vault_move_info_restore
import manhwa_viewer.presentation.generated.resources.vault_move_message_multiple
import manhwa_viewer.presentation.generated.resources.vault_move_message_single
import manhwa_viewer.presentation.generated.resources.vault_move_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FileBrowserScreen(
    onNavigateToViewer: (archiveId: Long, startPage: Int, sortByDate: Boolean, sortAscending: Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: FileBrowserViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val coverModels by viewModel.coverModels.collectAsState()
    val hazeState = rememberHazeState()

    // 선택 모드에서 뒤로가기 → 선택 모드 해제
    BackHandler(enabled = state.isSelectionMode) {
        viewModel.onIntent(FileBrowserIntent.ExitSelectionMode)
    }

    BackHandler(enabled = !state.isSelectionMode) {
        viewModel.onIntent(FileBrowserIntent.GoBack)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is FileBrowserSideEffect.NavigateToViewer -> onNavigateToViewer(effect.archiveId, effect.startPage, effect.sortByDate, effect.sortAscending)
                FileBrowserSideEffect.NavigateToHome -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            BaseTopBar(
                title = if (state.isSelectionMode) {
                    stringResource(Res.string.file_selection_count, state.selectedFileKeys.size)
                } else {
                    stringResource(Res.string.file_browser_title)
                },
                onBack = if (state.isSelectionMode) null else ({ viewModel.onIntent(FileBrowserIntent.GoBack) }),
                pathSegments = state.pathSegments,
                onSegmentClick = { viewModel.onIntent(FileBrowserIntent.GoToSegment(it)) },
                onHomeClick = onNavigateToHome,
                sortState = if (state.isSelectionMode) null else state.sortState,
                onSortFieldChange = { viewModel.onIntent(FileBrowserIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(FileBrowserIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(FileBrowserIntent.ToggleViewMode) },
                leadingContent = if (state.isSelectionMode) {
                    {
                        SelectAllCheckbox(
                            isAllSelected = state.selectedFileKeys.size == state.files.size && state.files.isNotEmpty(),
                            onClick = { viewModel.onIntent(FileBrowserIntent.ToggleSelectAll) },
                        )
                    }
                } else {
                    null
                },
                trailingActions = if (state.isSelectionMode) {
                    {
                        TextButton(onClick = { viewModel.onIntent(FileBrowserIntent.ExitSelectionMode) }) {
                            Text(
                                text = stringResource(Res.string.file_selection_cancel),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MangaLensColors.blueHi,
                            )
                        }
                    }
                } else if (state.insideArchivePath == null) {
                    {
                        IconButton(onClick = { viewModel.onIntent(FileBrowserIntent.ShowCreateFolderDialog) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_plus),
                                contentDescription = stringResource(Res.string.file_create_folder_title),
                                tint = MangaLensColors.blueHi,
                                modifier = Modifier.size(22.dp),
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
                    state.files.isEmpty() -> {
                        Text(
                            text = stringResource(Res.string.file_browser_empty),
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
                            fun coverModelFor(file: ArchiveFile) =
                                if (isInsideArchive) coverModels["${file.filePath}/${file.fileName}"]
                                else coverModels[file.filePath]

                            fun fileKey(file: ArchiveFile) = "${file.filePath}/${file.fileName}"

                            if (state.sortState.viewMode == ListViewMode.LIST) {
                                val columns = if (isLandscape) 2 else 1
                                if (columns == 1) {
                                    LazyColumn(
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(state.files, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                            FileBrowserFileItem(
                                                file = file,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = fileKey(file) in state.selectedFileKeys,
                                                onClick = {
                                                    if (state.isSelectionMode) {
                                                        viewModel.onIntent(FileBrowserIntent.ToggleSelection(file))
                                                    } else {
                                                        when {
                                                            file.type == ArchiveType.FOLDER -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                            state.insideArchivePath != null -> viewModel.onIntent(FileBrowserIntent.OpenArchiveEntry(file, file.lastReadIndex))
                                                            file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                            else -> viewModel.onIntent(FileBrowserIntent.OpenFile(file))
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!state.isSelectionMode) {
                                                        viewModel.onIntent(FileBrowserIntent.EnterSelectionMode(file))
                                                    }
                                                },
                                                coverModel = coverModelFor(file),
                                                insideArchive = isInsideArchive,
                                            )
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(state.files, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                            FileBrowserFileItem(
                                                file = file,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = fileKey(file) in state.selectedFileKeys,
                                                onClick = {
                                                    if (state.isSelectionMode) {
                                                        viewModel.onIntent(FileBrowserIntent.ToggleSelection(file))
                                                    } else {
                                                        when {
                                                            file.type == ArchiveType.FOLDER -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                            state.insideArchivePath != null -> viewModel.onIntent(FileBrowserIntent.OpenArchiveEntry(file, file.lastReadIndex))
                                                            file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                            else -> viewModel.onIntent(FileBrowserIntent.OpenFile(file))
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!state.isSelectionMode) {
                                                        viewModel.onIntent(FileBrowserIntent.EnterSelectionMode(file))
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
                                    items(state.files, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                        FileGridItem(
                                            archive = file,
                                            onClick = {
                                                if (state.isSelectionMode) {
                                                    viewModel.onIntent(FileBrowserIntent.ToggleSelection(file))
                                                } else {
                                                    when {
                                                        file.type == ArchiveType.FOLDER -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                        state.insideArchivePath != null -> viewModel.onIntent(FileBrowserIntent.OpenArchiveEntry(file, file.lastReadIndex))
                                                        file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                        else -> viewModel.onIntent(FileBrowserIntent.OpenFile(file))
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (!state.isSelectionMode) {
                                                    viewModel.onIntent(FileBrowserIntent.EnterSelectionMode(file))
                                                }
                                            },
                                            coverModel = coverModelFor(file),
                                            isSelectionMode = state.isSelectionMode,
                                            isSelected = fileKey(file) in state.selectedFileKeys,
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
                FileSelectionActionBar(
                    selectedCount = state.selectedFileKeys.size,
                    onMove = { viewModel.onIntent(FileBrowserIntent.MoveSelected) },
                    onVault = { viewModel.onIntent(FileBrowserIntent.VaultSelected) },
                    onDelete = { viewModel.onIntent(FileBrowserIntent.DeleteSelected) },
                    onRename = { viewModel.onIntent(FileBrowserIntent.RenameSelected) },
                )
            }
        }
    }

    // 이름 변경 다이얼로그
    val renameTarget = state.renameTarget
    if (state.showRenameDialog && renameTarget != null) {
        RenameDialog(
            initialName = renameTarget.fileName,
            siblingNames = state.files
                .filter { it.fileName != renameTarget.fileName }
                .map { it.fileName }
                .toSet(),
            hazeState = hazeState,
            onConfirm = { newName -> viewModel.onIntent(FileBrowserIntent.ConfirmRename(newName)) },
            onDismiss = { viewModel.onIntent(FileBrowserIntent.DismissRenameDialog) },
        )
    }

    // 삭제 확인 다이얼로그
    if (state.showDeleteDialog && state.deleteTargets.isNotEmpty()) {
        DeleteConfirmDialog(
            count = state.deleteTargets.size,
            firstName = state.deleteTargets.firstOrNull()?.fileName.orEmpty(),
            isFolder = state.deleteTargets.firstOrNull()?.type == ArchiveType.FOLDER,
            hazeState = hazeState,
            onConfirm = { viewModel.onIntent(FileBrowserIntent.ConfirmDelete) },
            onDismiss = { viewModel.onIntent(FileBrowserIntent.DismissDeleteDialog) },
        )
    }

    // 폴더 만들기 다이얼로그
    if (state.showCreateFolderDialog) {
        CreateFolderDialog(
            defaultName = state.defaultFolderName,
            siblingNames = state.files.map { it.fileName }.toSet(),
            hazeState = hazeState,
            onConfirm = { name -> viewModel.onIntent(FileBrowserIntent.ConfirmCreateFolder(name)) },
            onDismiss = { viewModel.onIntent(FileBrowserIntent.DismissCreateFolderDialog) },
        )
    }

    // 보관함 이동 다이얼로그
    if (state.showVaultDialog && state.vaultTargets.isNotEmpty()) {
        VaultMoveDialog(
            count = state.vaultTargets.size,
            firstName = state.vaultTargets.firstOrNull()?.fileName.orEmpty(),
            hazeState = hazeState,
            onConfirm = { viewModel.onIntent(FileBrowserIntent.ConfirmVault) },
            onDismiss = { viewModel.onIntent(FileBrowserIntent.DismissVaultDialog) },
        )
    }
}

// ── 전체 선택 체크박스 ────────────────────────────────────────────

@Composable
private fun SelectAllCheckbox(
    isAllSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SelectionCheckbox(isSelected = isAllSelected)
        Text(
            text = stringResource(Res.string.file_selection_select_all),
            style = MaterialTheme.typography.bodyMedium,
            color = MangaLensColors.onSurface,
        )
    }
}

// ── 선택 모드 체크박스 ────────────────────────────────────────────

@Composable
private fun SelectionCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = isSelected,
        modifier = modifier.size(24.dp),
    ) { checked ->
        Icon(
            painter = painterResource(
                if (checked) Res.drawable.ic_check_on else Res.drawable.ic_check_off,
            ),
            contentDescription = null,
            tint = if (checked) Color.Unspecified else MangaLensColors.textTertiary,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── 파일 탐색 전용 파일 아이템 (선택 모드 지원) ─────────────────────

@Composable
private fun FileBrowserFileItem(
    file: ArchiveFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
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
        leadingContent = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = scaleIn(
                    initialScale = 0f,
                    animationSpec = tween(durationMillis = 200),
                ) + expandHorizontally(
                    animationSpec = tween(durationMillis = 200),
                ),
                exit = scaleOut(
                    targetScale = 0f,
                    animationSpec = tween(durationMillis = 0),
                ) + shrinkHorizontally(
                    animationSpec = tween(durationMillis = 200),
                ),
            ) {
                Row {
                    SelectionCheckbox(isSelected = isSelected)
                    Spacer(modifier = Modifier.width(14.dp))
                }
            }
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

// ── 파일 탐색 선택 모드 하단 액션 바 ──────────────────────────────

@Composable
private fun FileSelectionActionBar(
    selectedCount: Int,
    onMove: () -> Unit,
    onVault: () -> Unit,
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
            FileActionButton(
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
            FileActionButton(
                icon = { tint ->
                    Icon(
                        painter = painterResource(Res.drawable.ic_lock),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = stringResource(Res.string.file_selection_vault),
                color = if (hasSelection) MangaLensColors.tilePurple else MangaLensColors.textTertiary,
                enabled = hasSelection,
                onClick = onVault,
            )
            FileActionButton(
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
                FileActionButton(
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

@Composable
private fun FileActionButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon(color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.24).sp,
            ),
            color = color,
        )
    }
}

// ── 이름 변경 다이얼로그 ──────────────────────────────────────────

@Composable
private fun RenameDialog(
    initialName: String,
    siblingNames: Set<String>,
    hazeState: HazeState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(0, initialName.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val value = textFieldValue.text
    val isConfirmEnabled = value.isNotBlank()
        && value != initialName
        && siblingNames.none { it.equals(value, ignoreCase = true) }

    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.file_selection_rename),
        confirmColor = MangaLensColors.blueHi,
        confirmEnabled = isConfirmEnabled,
        onConfirm = { onConfirm(value) },
    ) {
        // 헤더
        Text(
            text = stringResource(Res.string.file_selection_rename),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MangaLensColors.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        )

        // 입력 필드
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MangaLensColors.fillTertiary)
                .border(0.5.dp, MangaLensColors.separator, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(
                    vertical = 16.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MangaLensColors.onSurface,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MangaLensColors.blueHi),
                )

                if (value.isNotEmpty()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = MangaLensColors.textTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                textFieldValue = TextFieldValue("")
                            },
                    )
                }
            }
        }
    }
}

// ── 삭제 확인 다이얼로그 ─────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    count: Int,
    firstName: String,
    isFolder: Boolean,
    hazeState: HazeState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.file_delete_confirm),
        confirmColor = MangaLensColors.red,
        onConfirm = onConfirm,
    ) {
        // 헤더 + 메시지
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 타이틀
            Text(
                text = stringResource(Res.string.file_delete_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MangaLensColors.onSurface,
            )
            // 메시지
            val highlightStyle = SpanStyle(
                color = MangaLensColors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildAnnotatedString {
                    if (count == 1) {
                        val highlight = stringResource(Res.string.file_delete_message_single_highlight, firstName)
                        val suffix = if (isFolder) {
                            stringResource(Res.string.file_delete_message_single_folder_suffix)
                        } else {
                            stringResource(Res.string.file_delete_message_single_file_suffix)
                        }
                        withStyle(highlightStyle) { append(highlight) }
                        append(suffix)
                    } else {
                        val highlight = stringResource(Res.string.file_delete_message_multiple_highlight, count)
                        val suffix = stringResource(Res.string.file_delete_message_multiple_suffix)
                        withStyle(highlightStyle) { append(highlight) }
                        append(suffix)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MangaLensColors.textSecondary,
            )
            // 경고
            Text(
                text = stringResource(Res.string.file_delete_warning),
                style = MaterialTheme.typography.labelLarge,
                color = MangaLensColors.textTertiary,
            )
        }
    }
}

// ── 보관함 이동 다이얼로그 ──────────────────────────────────────────

@Composable
private fun VaultMoveDialog(
    count: Int,
    firstName: String,
    hazeState: HazeState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.vault_move_confirm),
        confirmColor = MangaLensColors.tilePurple,
        onConfirm = onConfirm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 잠금 아이콘
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MangaLensColors.tilePurple.copy(alpha = 0.15f))
                    .border(
                        0.5.dp,
                        MangaLensColors.tilePurple.copy(alpha = 0.25f),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = null,
                    tint = MangaLensColors.tilePurple,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 제목
            Text(
                text = stringResource(Res.string.vault_move_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MangaLensColors.onSurface,
            )
        }

        // 설명 박스
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MangaLensColors.tilePurple.copy(alpha = 0.15f))
                .border(
                    0.5.dp,
                    MangaLensColors.tilePurple.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 메시지
            Text(
                text = buildAnnotatedString {
                    if (count == 1) {
                        withStyle(
                            SpanStyle(
                                color = MangaLensColors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(stringResource(Res.string.vault_move_message_single, firstName))
                        }
                    } else {
                        withStyle(
                            SpanStyle(
                                color = MangaLensColors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(stringResource(Res.string.vault_move_message_multiple, count))
                        }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MangaLensColors.textSecondary,
            )

            // 정보 항목
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultInfoRow(
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_hide),
                            contentDescription = null,
                            tint = MangaLensColors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    text = stringResource(Res.string.vault_move_info_hidden),
                )
                VaultInfoRow(
                    icon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_folder_move),
                            contentDescription = null,
                            tint = MangaLensColors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    text = stringResource(Res.string.vault_move_info_restore),
                )
            }
        }
    }
}

@Composable
private fun VaultInfoRow(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MangaLensColors.textSecondary,
        )
    }
}

// ── 폴더 만들기 다이얼로그 ──────────────────────────────────────────

@Composable
private fun CreateFolderDialog(
    defaultName: String,
    siblingNames: Set<String>,
    hazeState: HazeState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = defaultName,
                selection = TextRange(0, defaultName.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val value = textFieldValue.text
    val hasInvalidChars = value.contains('/') || value.contains('\\') || value.contains("..")
    val isConfirmEnabled = value.isNotBlank()
        && !hasInvalidChars
        && siblingNames.none { it.equals(value, ignoreCase = true) }

    GlassConfirmDialog(
        hazeState = hazeState,
        onDismiss = onDismiss,
        confirmText = stringResource(Res.string.file_create_folder_confirm),
        confirmColor = MangaLensColors.blueHi,
        confirmEnabled = isConfirmEnabled,
        onConfirm = { onConfirm(value) },
    ) {
        // 헤더
        Text(
            text = stringResource(Res.string.file_create_folder_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MangaLensColors.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        )

        // 입력 필드
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MangaLensColors.fillTertiary)
                .border(0.5.dp, MangaLensColors.separator, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(
                    vertical = 16.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MangaLensColors.onSurface,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MangaLensColors.blueHi),
                )

                if (value.isNotEmpty()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = MangaLensColors.textTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                textFieldValue = TextFieldValue("")
                            },
                    )
                }
            }
        }
    }
}
