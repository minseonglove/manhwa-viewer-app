package com.manhwaviewer.presentation.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.screen.common.HomeTile
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_app_logo
import manhwa_viewer.presentation.generated.resources.ic_archive_box
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_edit_note
import manhwa_viewer.presentation.generated.resources.ic_folder_files
import manhwa_viewer.presentation.generated.resources.ic_history_clock
import manhwa_viewer.presentation.generated.resources.ic_image_gallery
import manhwa_viewer.presentation.generated.resources.ic_settings
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToViewer: (archiveId: Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToRecent: () -> Unit,
    onNavigateToImages: () -> Unit,
    onNavigateToVault: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is HomeSideEffect.NavigateToViewer -> onNavigateToViewer(effect.archiveId)
                is HomeSideEffect.NavigateToSettings -> onNavigateToSettings()
                is HomeSideEffect.NavigateToFiles -> onNavigateToFiles()
                is HomeSideEffect.NavigateToRecent -> onNavigateToRecent()
                is HomeSideEffect.NavigateToImages -> onNavigateToImages()
                is HomeSideEffect.NavigateToVault -> onNavigateToVault()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_app_logo),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("MangaLens")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(HomeIntent.NavigateToSettings) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_settings),
                            contentDescription = "설정",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                // Landscape: Row with 4 tiles + Divider + history button on right
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeTile(
                        icon = Res.drawable.ic_folder_files,
                        label = "파일",
                        subtitle = "내장저장공간",
                        iconBackgroundColor = MangaLensColors.tileBlue,
                        onClick = { viewModel.onIntent(HomeIntent.NavigateToFiles) },
                        modifier = Modifier.width(150.dp),
                    )
                    HomeTile(
                        icon = Res.drawable.ic_history_clock,
                        label = "최근",
                        subtitle = "${state.recentCount}개 항목",
                        iconBackgroundColor = MangaLensColors.tileAmber,
                        onClick = { viewModel.onIntent(HomeIntent.NavigateToRecent) },
                        modifier = Modifier.width(150.dp),
                    )
                    HomeTile(
                        icon = Res.drawable.ic_image_gallery,
                        label = "이미지",
                        subtitle = "${state.imageCount}개 항목",
                        iconBackgroundColor = MangaLensColors.tileGreen,
                        onClick = { viewModel.onIntent(HomeIntent.NavigateToImages) },
                        modifier = Modifier.width(150.dp),
                    )
                    HomeTile(
                        icon = Res.drawable.ic_archive_box,
                        label = "보관함",
                        subtitle = "${state.vaultFolderCount}개 폴더",
                        iconBackgroundColor = MangaLensColors.tilePurple,
                        onClick = { viewModel.onIntent(HomeIntent.NavigateToVault) },
                        modifier = Modifier.width(150.dp),
                    )
                    VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable { /* TODO: 번역 히스토리 */ },
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_edit_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "번역 히스토리",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "번역한 문장을 모아보기",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Icon(
                                painter = painterResource(Res.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                // Portrait: 2x2 grid + bottom history card
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        HomeTile(
                            icon = Res.drawable.ic_folder_files,
                            label = "파일",
                            subtitle = "내장저장공간",
                            iconBackgroundColor = MangaLensColors.tileBlue, // blue
                            onClick = { viewModel.onIntent(HomeIntent.NavigateToFiles) },
                        )
                    }
                    item {
                        HomeTile(
                            icon = Res.drawable.ic_history_clock,
                            label = "최근",
                            subtitle = "${state.recentCount}개 항목",
                            iconBackgroundColor = MangaLensColors.tileAmber, // amber
                            onClick = { viewModel.onIntent(HomeIntent.NavigateToRecent) },
                        )
                    }
                    item {
                        HomeTile(
                            icon = Res.drawable.ic_image_gallery,
                            label = "이미지",
                            subtitle = "${state.imageCount}개 항목",
                            iconBackgroundColor = MangaLensColors.tileGreen, // green
                            onClick = { viewModel.onIntent(HomeIntent.NavigateToImages) },
                        )
                    }
                    item {
                        HomeTile(
                            icon = Res.drawable.ic_archive_box,
                            label = "보관함",
                            subtitle = "${state.vaultFolderCount}개 폴더",
                            iconBackgroundColor = MangaLensColors.tilePurple, // purple
                            onClick = { viewModel.onIntent(HomeIntent.NavigateToVault) },
                        )
                    }

                    item(span = { GridItemSpan(2) }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { /* TODO: 번역 히스토리 */ },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MangaLensColors.primary.copy(alpha = 0.12f),
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_edit_note),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(11.dp).size(22.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "번역 히스토리",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "번역한 문장을 모아보고 검색할 수 있어요",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    painter = painterResource(Res.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
