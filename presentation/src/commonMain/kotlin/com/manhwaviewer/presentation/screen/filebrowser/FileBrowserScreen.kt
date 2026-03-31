package com.manhwaviewer.presentation.screen.filebrowser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SubHeader
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

    BackHandler {
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
            SubHeader(
                title = "파일",
                onBack = { viewModel.onIntent(FileBrowserIntent.GoBack) },
                pathSegments = state.pathSegments,
                onSegmentClick = { viewModel.onIntent(FileBrowserIntent.GoToSegment(it)) },
                onHomeClick = onNavigateToHome,
                sortState = state.sortState,
                onSortFieldChange = { viewModel.onIntent(FileBrowserIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(FileBrowserIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(FileBrowserIntent.ToggleViewMode) },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val isLandscape = maxWidth > maxHeight
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.files.isEmpty() -> {
                    Text(
                        text = "파일이 없습니다",
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
                        if (state.sortState.viewMode == ListViewMode.LIST) {
                            val columns = if (isLandscape) 2 else 1
                            if (columns == 1) {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.files, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                        FileItem(
                                            archive = file,
                                            onClick = {
                                                when {
                                                    file.type == ArchiveType.FOLDER -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                    state.insideArchivePath != null -> viewModel.onIntent(FileBrowserIntent.OpenArchiveEntry(file, file.lastReadIndex))
                                                    file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                    else -> viewModel.onIntent(FileBrowserIntent.OpenFile(file))
                                                }
                                            },
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
                                        FileItem(
                                            archive = file,
                                            onClick = {
                                                when {
                                                    file.type == ArchiveType.FOLDER -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                    state.insideArchivePath != null -> viewModel.onIntent(FileBrowserIntent.OpenArchiveEntry(file, file.lastReadIndex))
                                                    file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                    else -> viewModel.onIntent(FileBrowserIntent.OpenFile(file))
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            val columns = if (isLandscape) 9 else 4
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.files, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                    FileGridItem(
                                        archive = file,
                                        onClick = {
                                            when {
                                                file.type == ArchiveType.FOLDER -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                state.insideArchivePath != null -> viewModel.onIntent(FileBrowserIntent.OpenArchiveEntry(file, file.lastReadIndex))
                                                file.type in setOf(ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR) -> viewModel.onIntent(FileBrowserIntent.NavigateToFolder(file.filePath))
                                                else -> viewModel.onIntent(FileBrowserIntent.OpenFile(file))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
