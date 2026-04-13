package com.manhwaviewer.presentation.screen.recent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.manhwaviewer.presentation.screen.common.SubHeader
import com.manhwaviewer.presentation.screen.common.displayName
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
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
            SubHeader(
                title = "최근",
                onBack = onBack,
                sortState = state.sortState,
                onSortFieldChange = { viewModel.onIntent(RecentIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(RecentIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(RecentIntent.ToggleViewMode) },
                trailingActions = {
                    if (state.archives.isNotEmpty()) {
                        TextButton(onClick = { viewModel.onIntent(RecentIntent.ShowClearAllConfirm) }) {
                            Text(
                                text = "전체 비우기",
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .hazeSource(hazeState),
            ) {
                val isLandscape = maxWidth > maxHeight
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.archives.isEmpty() -> {
                        Text(
                            text = "최근 읽은 항목이 없습니다",
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.archives, key = { it.id }) { archive ->
                                        RecentFileItem(
                                            archive = archive,
                                            onClick = { viewModel.onIntent(RecentIntent.OpenArchive(archive)) },
                                            onDelete = { viewModel.onIntent(RecentIntent.ShowDeleteConfirm(archive)) },
                                            coverModel = state.coverModels[archive.id],
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.archives, key = { it.id }) { archive ->
                                        RecentFileItem(
                                            archive = archive,
                                            onClick = { viewModel.onIntent(RecentIntent.OpenArchive(archive)) },
                                            onDelete = { viewModel.onIntent(RecentIntent.ShowDeleteConfirm(archive)) },
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
                                        onClick = { viewModel.onIntent(RecentIntent.OpenArchive(archive)) },
                                        coverModel = state.coverModels[archive.id],
                                    )
                                }
                            }
                        }
                    }
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
                        .background(Color.Black.copy(alpha = 0.48f))
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

// ── 최근 화면 전용 파일 아이템 (삭제 버튼 포함) ──────────────────────

@Composable
private fun RecentFileItem(
    archive: ArchiveFile,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    coverModel: com.manhwaviewer.presentation.screen.viewer.ViewerPageModel?,
) {
    FileItem(
        archive = archive,
        onClick = onClick,
        coverModel = coverModel,
        showProgress = true,
        showFileType = false,
        showFolderCover = true,
        trailingContent = {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = "삭제",
                    tint = MangaLensColors.red.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
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
            val (title, description) = when (type) {
                is RecentBottomSheetType.ClearAll -> "최근 기록 전체 삭제" to buildAnnotatedString {
                    append("${archiveCount}개 기록이 모두 삭제됩니다.\n이 작업은 되돌릴 수 없습니다.")
                }
                is RecentBottomSheetType.DeleteSingle -> "기록 삭제" to buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                        append("\"${type.archive.displayName()}\"")
                    }
                    append(" 기록을 삭제합니다.\n이 작업은 되돌릴 수 없습니다.")
                }
                RecentBottomSheetType.None -> "" to buildAnnotatedString { }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
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
                is RecentBottomSheetType.ClearAll -> "전체 비우기"
                is RecentBottomSheetType.DeleteSingle -> "삭제"
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
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(0.5.dp, MangaLensColors.separator, RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "취소",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
