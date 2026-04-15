package com.manhwaviewer.presentation.screen.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.screen.common.BaseTopBar
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_lock
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VaultScreen(
    onNavigateToViewer: (archiveId: Long, startPage: Int, sortByDate: Boolean, sortAscending: Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: VaultViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val coverModels by viewModel.coverModels.collectAsState()

    BackHandler {
        viewModel.onIntent(VaultIntent.GoBack)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is VaultSideEffect.NavigateToViewer -> onNavigateToViewer(
                    effect.archiveId, effect.startPage, effect.sortByDate, effect.sortAscending,
                )
                VaultSideEffect.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            BaseTopBar(
                title = "보관함",
                onBack = { viewModel.onIntent(VaultIntent.GoBack) },
                pathSegments = state.pathSegments,
                onSegmentClick = { viewModel.onIntent(VaultIntent.GoToSegment(it)) },
                onHomeClick = onNavigateToHome,
                sortState = state.sortState,
                onSortFieldChange = { viewModel.onIntent(VaultIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(VaultIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(VaultIntent.ToggleViewMode) },
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
                state.archiveFiles.isEmpty() -> {
                    Text(
                        text = "보관함이 비어있습니다",
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

                        if (state.sortState.viewMode == ListViewMode.LIST) {
                            val columns = if (isLandscape) 2 else 1
                            if (columns == 1) {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.archiveFiles, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                        FileItem(
                                            archive = file,
                                            onClick = { handleFileClick(file, state, viewModel) },
                                            coverModel = coverModelFor(file),
                                            insideArchive = isInsideArchive,
                                            showLockBadge = false,
                                        )
                                    }
                                    if (isAtRoot) {
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.archiveFiles, key = { "${it.filePath}/${it.fileName}" }) { file ->
                                        FileItem(
                                            archive = file,
                                            onClick = { handleFileClick(file, state, viewModel) },
                                            coverModel = coverModelFor(file),
                                            insideArchive = isInsideArchive,
                                            showLockBadge = false,
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
                                        onClick = { handleFileClick(file, state, viewModel) },
                                        coverModel = coverModelFor(file),
                                        showLockBadge = false,
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
                    text = "보관함",
                    style = MaterialTheme.typography.labelMedium,
                    color = MangaLensColors.tilePurple,
                )
            }
            Text(
                text = "보관함에 저장된 콘텐츠는\n기기 갤러리에 노출되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
