package com.manhwaviewer.presentation.screen.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.VaultFile
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.BaseTopBar
import com.manhwaviewer.presentation.theme.MangaLensColors
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
            BaseTopBar(
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
                                    items(state.files, key = { it.relativePath }) { file ->
                                        VaultFileListItem(
                                            file = file,
                                            onClick = { viewModel.onIntent(VaultIntent.OpenFile(file)) },
                                        )
                                    }
                                }
                                VaultInfoCard(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .fillMaxHeight()
                                        .padding(16.dp),
                                    alpha = 0.15f,
                                    showBorder = true,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    VaultInfoCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        alpha = 0.08f,
                                        showBorder = false,
                                    )
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
                                items(state.files, key = { it.relativePath }) { file ->
                                    VaultFileListItem(
                                        file = file,
                                        onClick = { viewModel.onIntent(VaultIntent.OpenFile(file)) },
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
                            items(state.files, key = { it.relativePath }) { file ->
                                VaultFileGridItem(
                                    file = file,
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

@Composable
private fun VaultFileListItem(
    file: VaultFile,
    onClick: () -> Unit,
) {
    val fileName = file.relativePath.substringAfterLast('/')
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_lock),
                contentDescription = null,
                tint = MangaLensColors.tilePurple,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VaultFileGridItem(
    file: VaultFile,
    onClick: () -> Unit,
) {
    val fileName = file.relativePath.substringAfterLast('/')
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_lock),
                contentDescription = null,
                tint = MangaLensColors.tilePurple,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VaultInfoCard(
    modifier: Modifier = Modifier,
    alpha: Float,
    showBorder: Boolean,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MangaLensColors.tilePurple.copy(alpha = alpha),
        ),
        border = if (showBorder) {
            BorderStroke(0.5.dp, MangaLensColors.tilePurple.copy(alpha = 0.2f))
        } else {
            null
        },
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
