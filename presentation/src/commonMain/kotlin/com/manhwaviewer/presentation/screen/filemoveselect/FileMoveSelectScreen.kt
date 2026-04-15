package com.manhwaviewer.presentation.screen.filemoveselect

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.presentation.screen.common.Breadcrumb
import com.manhwaviewer.presentation.screen.common.CreateFolderDialog
import com.manhwaviewer.presentation.screen.filebrowser.NavigationDirection
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_move_banner
import manhwa_viewer.presentation.generated.resources.file_move_cancel
import manhwa_viewer.presentation.generated.resources.file_move_confirm
import manhwa_viewer.presentation.generated.resources.file_move_empty_folders
import manhwa_viewer.presentation.generated.resources.file_move_title
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_file_folder
import manhwa_viewer.presentation.generated.resources.ic_plus
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FileMoveSelectScreen(
    onMoveCompleted: () -> Unit,
    onBack: () -> Unit,
    viewModel: FileMoveSelectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val hazeState = rememberHazeState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                FileMoveSelectSideEffect.MoveCompleted -> onMoveCompleted()
                is FileMoveSelectSideEffect.MoveError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                FileMoveSelectSideEffect.NavigateBack -> onBack()
            }
        }
    }

    BackHandler {
        viewModel.onIntent(FileMoveSelectIntent.GoBack)
    }

    val listState = rememberLazyListState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp),
            ) {
                // TopBar: 취소 | 이동 | +
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.file_move_cancel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onBack),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.file_move_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MangaLensColors.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.onIntent(FileMoveSelectIntent.ShowCreateFolderDialog) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_plus),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // 브레드크럼 — 이동 화면에서는 홈 아이콘 없이 "내장 저장공간"부터 시작
                Breadcrumb(
                    pathSegments = state.pathSegments,
                    onSegmentClick = { viewModel.onIntent(FileMoveSelectIntent.GoToSegment(it)) },
                    onHomeClick = { viewModel.onIntent(FileMoveSelectIntent.GoToSegment(0)) },
                    showHomeIcon = false,
                )
            }
        },
        bottomBar = {
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
                    val enabled = state.isMoveEnabled
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (enabled) Modifier.clickable {
                                    viewModel.onIntent(FileMoveSelectIntent.ConfirmMove)
                                } else Modifier,
                            )
                            .background(
                                color = if (enabled) MaterialTheme.colorScheme.primary
                                else MangaLensColors.fillTertiary,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .border(
                                width = 0.5.dp,
                                color = if (enabled) MaterialTheme.colorScheme.primary
                                else MangaLensColors.separator,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isMoving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MangaLensColors.onSurface,
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.file_move_confirm),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.24).sp,
                                ),
                                color = if (enabled) MangaLensColors.onSurface
                                else MangaLensColors.textTertiary,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .hazeSource(hazeState),
        ) {
            // 이동 배너
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .background(
                        color = MangaLensColors.blue.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .border(
                        width = 0.5.dp,
                        color = MangaLensColors.blue.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_file_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(Res.string.file_move_banner, state.itemCount),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 폴더 목록
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                AnimatedContent(
                    targetState = state.currentPath,
                    transitionSpec = {
                        val direction = state.navigationDirection
                        if (direction == NavigationDirection.FORWARD) {
                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                        } else {
                            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                        }
                    },
                ) { _ ->
                    if (state.folders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            color = MangaLensColors.fillTertiary,
                                            shape = RoundedCornerShape(12.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_file_folder),
                                        contentDescription = null,
                                        tint = MangaLensColors.textTertiary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(Res.string.file_move_empty_folders),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MangaLensColors.textTertiary,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val folders = state.folders
                            items(
                                count = folders.size,
                                key = { folders[it].filePath },
                            ) { index ->
                                val folder = folders[index]
                                FolderMoveItem(
                                    folder = folder,
                                    onClick = {
                                        viewModel.onIntent(
                                            FileMoveSelectIntent.NavigateToFolder(folder.filePath),
                                        )
                                    },
                                    showDivider = index < folders.lastIndex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 폴더 생성 다이얼로그
    if (state.showCreateFolderDialog) {
        CreateFolderDialog(
            defaultName = state.defaultFolderName,
            siblingNames = state.folders.map { it.fileName }.toSet(),
            hazeState = hazeState,
            onConfirm = { viewModel.onIntent(FileMoveSelectIntent.ConfirmCreateFolder(it)) },
            onDismiss = { viewModel.onIntent(FileMoveSelectIntent.DismissCreateFolderDialog) },
        )
    }
}

@Composable
private fun FolderMoveItem(
    folder: com.manhwaviewer.domain.entity.ArchiveFile,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 폴더 아이콘
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MangaLensColors.blue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_file_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            // 폴더명
            Text(
                text = folder.fileName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.224).sp,
                ),
                color = MangaLensColors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            // 화살표
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MangaLensColors.textTertiary.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 78.dp),
                color = MangaLensColors.separator,
            )
        }
    }
}
