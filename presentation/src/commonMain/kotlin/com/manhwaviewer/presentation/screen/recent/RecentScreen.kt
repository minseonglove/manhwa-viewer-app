package com.manhwaviewer.presentation.screen.recent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.BaseTopBar
import com.manhwaviewer.presentation.screen.common.SelectionCheckbox
import com.manhwaviewer.presentation.screen.common.SelectionLeadingContent
import com.manhwaviewer.presentation.screen.common.displayName
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_delete
import manhwa_viewer.presentation.generated.resources.recent_cancel
import manhwa_viewer.presentation.generated.resources.recent_clear_all
import manhwa_viewer.presentation.generated.resources.recent_clear_all_description
import manhwa_viewer.presentation.generated.resources.recent_clear_all_title
import manhwa_viewer.presentation.generated.resources.recent_delete
import manhwa_viewer.presentation.generated.resources.recent_delete_selected_confirm
import manhwa_viewer.presentation.generated.resources.recent_delete_selected_description
import manhwa_viewer.presentation.generated.resources.recent_delete_selected_title
import manhwa_viewer.presentation.generated.resources.recent_delete_single_description_name
import manhwa_viewer.presentation.generated.resources.recent_delete_single_description_suffix
import manhwa_viewer.presentation.generated.resources.recent_delete_single_title
import manhwa_viewer.presentation.generated.resources.recent_empty
import manhwa_viewer.presentation.generated.resources.recent_selection_count
import manhwa_viewer.presentation.generated.resources.recent_selection_delete
import manhwa_viewer.presentation.generated.resources.recent_selection_empty_hint
import manhwa_viewer.presentation.generated.resources.recent_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RecentScreen(
    onNavigateToViewer: (archiveId: Long) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: RecentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val hazeState = rememberHazeState()
    val scope = rememberCoroutineScope()

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // 선택 모드에서 뒤로가기 → 선택 모드 해제
    BackHandler(enabled = state.isSelectionMode) {
        viewModel.onIntent(RecentIntent.ExitSelectionMode)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is RecentSideEffect.NavigateToViewer -> onNavigateToViewer(effect.archiveId)
            }
        }
    }

    // Sync bottom sheet with state
    LaunchedEffect(state.bottomSheetType) {
        if (state.bottomSheetType != RecentBottomSheetType.None) {
            sheetState.expand()
        } else {
            sheetState.partialExpand()
        }
    }

    // Dismiss on external collapse (drag down)
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.collect { value ->
            if (value == SheetValue.PartiallyExpanded && state.bottomSheetType != RecentBottomSheetType.None) {
                viewModel.onIntent(RecentIntent.DismissBottomSheet)
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = null,
        sheetContainerColor = Color.Transparent,
        sheetShadowElevation = 0.dp,
        sheetTonalElevation = 0.dp,
        sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        sheetContent = {
            RecentDeleteSheet(
                type = state.bottomSheetType,
                archiveCount = state.archives.size,
                hazeState = hazeState,
                onConfirm = { viewModel.onIntent(RecentIntent.ConfirmAction) },
                onDismiss = {
                    scope.launch { sheetState.partialExpand() }
                    viewModel.onIntent(RecentIntent.DismissBottomSheet)
                },
            )
        },
        topBar = {
            BaseTopBar(
                title = if (state.isSelectionMode) {
                    stringResource(Res.string.recent_selection_count, state.selectedArchiveIds.size)
                } else {
                    stringResource(Res.string.recent_title)
                },
                onBack = if (state.isSelectionMode) null else onBack,
                sortState = if (state.isSelectionMode) null else state.sortState,
                onSortFieldChange = { viewModel.onIntent(RecentIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(RecentIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(RecentIntent.ToggleViewMode) },
                trailingActions = {
                    if (state.isSelectionMode) {
                        TextButton(onClick = { viewModel.onIntent(RecentIntent.ExitSelectionMode) }) {
                            Text(
                                text = stringResource(Res.string.recent_cancel),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MangaLensColors.blueHi,
                            )
                        }
                    } else if (state.archives.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.onIntent(RecentIntent.ShowClearAllConfirm) },
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.recent_clear_all),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MangaLensColors.red,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .hazeSource(hazeState),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.weight(1f),
                ) {
                    val isLandscape = maxWidth > maxHeight
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        state.archives.isEmpty() -> {
                            Text(
                                text = stringResource(Res.string.recent_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        else -> {
                            if (state.sortState.viewMode == ListViewMode.LIST) {
                                if (isLandscape) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(state.archives, key = { _, archive -> archive.id }) { index, archive ->
                                            RecentFileItem(
                                                archive = archive,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = archive.id in state.selectedArchiveIds,
                                                showDivider = index < state.archives.lastIndex,
                                                onClick = {
                                                    if (state.isSelectionMode) {
                                                        viewModel.onIntent(RecentIntent.ToggleSelection(archive))
                                                    } else {
                                                        viewModel.onIntent(RecentIntent.OpenArchive(archive))
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!state.isSelectionMode) {
                                                        viewModel.onIntent(RecentIntent.EnterSelectionMode(archive))
                                                    }
                                                },
                                                onDelete = {
                                                    viewModel.onIntent(RecentIntent.ShowDeleteConfirm(archive))
                                                },
                                                coverModel = state.coverModels[archive.id],
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(16.dp),
                                    ) {
                                        itemsIndexed(state.archives, key = { _, archive -> archive.id }) { index, archive ->
                                            RecentFileItem(
                                                archive = archive,
                                                isSelectionMode = state.isSelectionMode,
                                                isSelected = archive.id in state.selectedArchiveIds,
                                                showDivider = index < state.archives.lastIndex,
                                                onClick = {
                                                    if (state.isSelectionMode) {
                                                        viewModel.onIntent(RecentIntent.ToggleSelection(archive))
                                                    } else {
                                                        viewModel.onIntent(RecentIntent.OpenArchive(archive))
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!state.isSelectionMode) {
                                                        viewModel.onIntent(RecentIntent.EnterSelectionMode(archive))
                                                    }
                                                },
                                                onDelete = {
                                                    viewModel.onIntent(RecentIntent.ShowDeleteConfirm(archive))
                                                },
                                                coverModel = state.coverModels[archive.id],
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
                                    items(state.archives, key = { it.id }) { archive ->
                                        FileGridItem(
                                            archive = archive,
                                            onClick = {
                                                if (state.isSelectionMode) {
                                                    viewModel.onIntent(RecentIntent.ToggleSelection(archive))
                                                } else {
                                                    viewModel.onIntent(RecentIntent.OpenArchive(archive))
                                                }
                                            },
                                            onLongClick = {
                                                if (!state.isSelectionMode) {
                                                    viewModel.onIntent(RecentIntent.EnterSelectionMode(archive))
                                                }
                                            },
                                            coverModel = state.coverModels[archive.id],
                                            isSelectionMode = state.isSelectionMode,
                                            isSelected = archive.id in state.selectedArchiveIds,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 선택 모드 하단 액션 바 ──
                AnimatedVisibility(
                    visible = state.isSelectionMode,
                    enter = slideInVertically(initialOffsetY = { it }) + expandVertically(expandFrom = Alignment.Bottom),
                    exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    SelectionActionBar(
                        selectedCount = state.selectedArchiveIds.size,
                        onDeleteClick = { viewModel.onIntent(RecentIntent.ShowDeleteSelectedConfirm) },
                    )
                }
            }

            // Scrim overlay (between content and sheet)
            AnimatedVisibility(
                visible = state.bottomSheetType != RecentBottomSheetType.None,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MangaLensColors.scrim)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            scope.launch { sheetState.partialExpand() }
                            viewModel.onIntent(RecentIntent.DismissBottomSheet)
                        },
                )
            }
        }
    }
}

// ── 최근 화면 전용 파일 아이템 (선택 모드 + 삭제 버튼) ─────────────

@Composable
private fun RecentFileItem(
    archive: ArchiveFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showDivider: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    coverModel: ViewerPageModel?,
) {
    FileItem(
        archive = archive,
        onClick = onClick,
        onLongClick = onLongClick,
        coverModel = coverModel,
        showProgress = true,
        showFileType = false,
        showFolderCover = true,
        showDivider = showDivider,
        leadingContent = {
            SelectionLeadingContent(
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                uncheckedTint = MangaLensColors.checkboxUncheckedLight,
            )
        },
        trailingContent = if (isSelectionMode) {
            {}
        } else {
            {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.recent_delete),
                        tint = MangaLensColors.red.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        modifier = Modifier.background(
            if (isSelected) MangaLensColors.blue.copy(alpha = 0.08f) else Color.Transparent,
        ),
    )
}

// ── 선택 모드 하단 액션 바 ──────────────────────────────────────

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onDeleteClick: () -> Unit,
) {
    val hasSelection = selectedCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalDivider(color = MangaLensColors.separator)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (hasSelection) MangaLensColors.red.copy(alpha = 0.14f)
                        else MangaLensColors.cancelButtonBg,
                    )
                    .border(
                        0.5.dp,
                        if (hasSelection) MangaLensColors.red.copy(alpha = 0.3f)
                        else MangaLensColors.separator,
                        RoundedCornerShape(14.dp),
                    )
                    .then(
                        if (hasSelection) Modifier.clickable(onClick = onDeleteClick) else Modifier,
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (hasSelection) {
                        stringResource(Res.string.recent_selection_delete, selectedCount)
                    } else {
                        stringResource(Res.string.recent_selection_empty_hint)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (hasSelection) MangaLensColors.red else MangaLensColors.textTertiary,
                )
            }
        }
    }
}

// ── 삭제 확인 바텀시트 ──────────────────────────────────────────────

@Composable
private fun RecentDeleteSheet(
    type: RecentBottomSheetType,
    archiveCount: Int,
    hazeState: HazeState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (type == RecentBottomSheetType.None) {
        Spacer(modifier = Modifier.height(1.dp))
        return
    }

    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MangaLensColors.surface1,
                    tints = listOf(HazeTint(MangaLensColors.surfaceGlass)),
                    blurRadius = 28.dp,
                    noiseFactor = 0.12f,
                ),
            )
            .border(0.5.dp, MangaLensColors.separator, sheetShape)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .background(MangaLensColors.fillSecondary, RoundedCornerShape(3.dp)),
            )
        }

        // Title & Description
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            val clearAllDescription = stringResource(Res.string.recent_clear_all_description, archiveCount)
            val deleteSingleName = if (type is RecentBottomSheetType.DeleteSingle) {
                stringResource(Res.string.recent_delete_single_description_name, type.archive.displayName())
            } else ""
            val deleteSingleSuffix = stringResource(Res.string.recent_delete_single_description_suffix)

            val (title, description) = when (type) {
                is RecentBottomSheetType.ClearAll -> stringResource(Res.string.recent_clear_all_title) to buildAnnotatedString {
                    append(clearAllDescription)
                }
                is RecentBottomSheetType.DeleteSingle -> stringResource(Res.string.recent_delete_single_title) to buildAnnotatedString {
                    withStyle(SpanStyle(color = MangaLensColors.textPrimary, fontWeight = FontWeight.SemiBold)) {
                        append(deleteSingleName)
                    }
                    append(deleteSingleSuffix)
                }
                is RecentBottomSheetType.DeleteSelected -> {
                    val count = type.archiveIds.size
                    stringResource(Res.string.recent_delete_selected_title, count) to buildAnnotatedString {
                        append(stringResource(Res.string.recent_delete_selected_description, count))
                    }
                }
                RecentBottomSheetType.None -> "" to buildAnnotatedString { }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MangaLensColors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MangaLensColors.textSecondary,
                lineHeight = 20.sp,
            )
        }

        // Buttons
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val confirmLabel = when (type) {
                is RecentBottomSheetType.ClearAll -> stringResource(Res.string.recent_clear_all)
                is RecentBottomSheetType.DeleteSingle -> stringResource(Res.string.recent_delete)
                is RecentBottomSheetType.DeleteSelected -> stringResource(
                    Res.string.recent_delete_selected_confirm,
                    type.archiveIds.size,
                )
                RecentBottomSheetType.None -> ""
            }

            // Confirm (red) button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MangaLensColors.red.copy(alpha = 0.14f))
                    .border(0.5.dp, MangaLensColors.red.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onConfirm)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MangaLensColors.red,
                )
            }

            // Cancel button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MangaLensColors.cancelButtonBg)
                    .border(0.5.dp, MangaLensColors.separator, RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.recent_cancel),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MangaLensColors.cancelButtonText,
                )
            }
        }
    }
}
