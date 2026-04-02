package com.manhwaviewer.presentation.screen.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.manhwaviewer.presentation.theme.MangaLensColors
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.screen.common.FileGridItem
import com.manhwaviewer.presentation.screen.common.FileItem
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SubHeader
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_lock
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VaultScreen(
    onNavigateToViewer: (archiveId: Long) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: VaultViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is VaultSideEffect.NavigateToViewer -> onNavigateToViewer(effect.archiveId)
            }
        }
    }

    Scaffold(
        topBar = {
            SubHeader(
                title = "보관함",
                onBack = onBack,
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
                else -> {
                    if (state.sortState.viewMode == ListViewMode.LIST) {
                        if (isLandscape) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (state.files.isEmpty()) {
                                        item {
                                            Text(
                                                text = "보관함이 비어있습니다",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(32.dp),
                                            )
                                        }
                                    }
                                    items(state.files, key = { it.filePath }) { file ->
                                        FileItem(
                                            archive = file,
                                            onClick = { viewModel.onIntent(VaultIntent.OpenFile(file)) },
                                        )
                                    }
                                }
                                Card(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .fillMaxHeight()
                                        .padding(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MangaLensColors.tilePurple.copy(alpha = 0.08f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_lock),
                                            contentDescription = null,
                                            tint = MangaLensColors.tilePurple,
                                        )
                                        Text(text = "보관함", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "보관함에 저장된 콘텐츠는\n기기 갤러리에 노출되지 않습니다.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MangaLensColors.tilePurple.copy(alpha = 0.08f),
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(Res.drawable.ic_lock),
                                                contentDescription = null,
                                                tint = MangaLensColors.tilePurple,
                                            )
                                            Text(text = "보관함", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                text = "보관함에 저장된 콘텐츠는\n기기 갤러리에 노출되지 않습니다.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (state.files.isEmpty()) {
                                    item {
                                        Text(
                                            text = "보관함이 비어있습니다",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        )
                                    }
                                }
                                items(state.files, key = { it.filePath }) { file ->
                                    FileItem(
                                        archive = file,
                                        onClick = { viewModel.onIntent(VaultIntent.OpenFile(file)) },
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
                            items(state.files, key = { it.filePath }) { file ->
                                FileGridItem(
                                    archive = file,
                                    onClick = { viewModel.onIntent(VaultIntent.OpenFile(file)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
