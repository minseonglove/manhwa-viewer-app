package com.manhwaviewer.presentation.screen.recent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SubHeader
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RecentScreen(
    onNavigateToViewer: (archiveId: Long) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: RecentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is RecentSideEffect.NavigateToViewer -> onNavigateToViewer(effect.archiveId)
            }
        }
    }

    Scaffold(
        topBar = {
            SubHeader(
                title = "최근",
                sortState = state.sortState,
                onSortFieldChange = { viewModel.onIntent(RecentIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(RecentIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(RecentIntent.ToggleViewMode) },
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
                                    FileItem(
                                        archive = archive,
                                        onClick = { viewModel.onIntent(RecentIntent.OpenArchive(archive)) },
                                        showProgress = true,
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.archives, key = { it.id }) { archive ->
                                    FileItem(
                                        archive = archive,
                                        onClick = { viewModel.onIntent(RecentIntent.OpenArchive(archive)) },
                                        showProgress = true,
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
                            items(state.archives, key = { it.id }) { archive ->
                                FileGridItem(
                                    archive = archive,
                                    onClick = { viewModel.onIntent(RecentIntent.OpenArchive(archive)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
