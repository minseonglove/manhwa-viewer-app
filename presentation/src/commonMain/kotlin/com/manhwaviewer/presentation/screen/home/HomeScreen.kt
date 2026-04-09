package com.manhwaviewer.presentation.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.presentation.screen.common.HomeTile
import com.manhwaviewer.presentation.screen.viewer.ViewerPageModel
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.app_name
import manhwa_viewer.presentation.generated.resources.home_continue_reading
import manhwa_viewer.presentation.generated.resources.home_recent_empty_subtitle
import manhwa_viewer.presentation.generated.resources.home_recent_empty_title
import manhwa_viewer.presentation.generated.resources.home_recent_title
import manhwa_viewer.presentation.generated.resources.home_recent_view_all
import manhwa_viewer.presentation.generated.resources.home_search
import manhwa_viewer.presentation.generated.resources.home_settings
import manhwa_viewer.presentation.generated.resources.home_shortcut_files
import manhwa_viewer.presentation.generated.resources.home_shortcut_files_sub
import manhwa_viewer.presentation.generated.resources.home_shortcut_images
import manhwa_viewer.presentation.generated.resources.home_shortcut_images_count
import manhwa_viewer.presentation.generated.resources.home_shortcut_vault
import manhwa_viewer.presentation.generated.resources.home_shortcut_vault_sub
import manhwa_viewer.presentation.generated.resources.ic_app_logo
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_search
import manhwa_viewer.presentation.generated.resources.ic_folder_home
import manhwa_viewer.presentation.generated.resources.ic_history_clock
import manhwa_viewer.presentation.generated.resources.ic_image_home
import manhwa_viewer.presentation.generated.resources.ic_lock_home
import manhwa_viewer.presentation.generated.resources.ic_settings
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToViewer: (archiveId: Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
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
                is HomeSideEffect.NavigateToSearch -> onNavigateToSearch()
                is HomeSideEffect.NavigateToFiles -> onNavigateToFiles()
                is HomeSideEffect.NavigateToRecent -> onNavigateToRecent()
                is HomeSideEffect.NavigateToImages -> onNavigateToImages()
                is HomeSideEffect.NavigateToVault -> onNavigateToVault()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_app_logo),
                                contentDescription = null,
                                tint = MangaLensColors.primary,
                            )
                            Text(stringResource(Res.string.app_name))
                        }
                    },
                    actions = {
                        IconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = { viewModel.onIntent(HomeIntent.NavigateToSearch) },
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_search),
                                contentDescription = stringResource(Res.string.home_search),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = { viewModel.onIntent(HomeIntent.NavigateToSettings) },
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_settings),
                                contentDescription = stringResource(Res.string.home_settings),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MangaLensColors.separatorLight)
            }
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val isLandscape = maxWidth > maxHeight
            val heroHeight = if (isLandscape) 88.dp else 140.dp

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                state.continueReading?.let { archive ->
                    item {
                        ContinueReadingHeroCard(
                            archive = archive,
                            coverModel = state.continueReadingCoverModel,
                            cardHeight = heroHeight,
                            onClick = {
                                viewModel.onIntent(HomeIntent.OpenArchive(archive.id))
                            },
                        )
                    }
                }

                item {
                    ShortcutGrid(
                        state = state,
                        onNavigateToFiles = { viewModel.onIntent(HomeIntent.NavigateToFiles) },
                        onNavigateToImages = { viewModel.onIntent(HomeIntent.NavigateToImages) },
                        onNavigateToVault = { viewModel.onIntent(HomeIntent.NavigateToVault) },
                    )
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecentSectionHeader(
                            onViewAll = { viewModel.onIntent(HomeIntent.NavigateToRecent) },
                        )
                        if (state.recentArchives.isEmpty()) {
                            EmptyRecentState()
                        } else {
                            RecentHorizontalList(
                                archives = state.recentArchives,
                                coverModels = state.recentCoverModels,
                                compact = isLandscape,
                                onArchiveClick = { archiveId ->
                                    viewModel.onIntent(HomeIntent.OpenArchive(archiveId))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingHeroCard(
    archive: ArchiveFile,
    coverModel: ViewerPageModel?,
    cardHeight: Dp,
    onClick: () -> Unit,
) {
    val progress = if (archive.totalPages > 0) {
        ((archive.lastReadIndex + 1).toFloat() / archive.totalPages).coerceIn(0f, 1f)
    } else {
        0f
    }
    val cardBg = MangaLensColors.surface1

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(cardHeight),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverModel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(4f / 3f)
                        .align(Alignment.CenterEnd),
                ) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to cardBg,
                                        0.65f to Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.home_continue_reading),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MangaLensColors.textPrimary,
                )

                Text(
                    text = archive.fileName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        shadow = Shadow(
                            color = MangaLensColors.black.copy(alpha = 0.9f),
                            offset = Offset(0f, 1f),
                            blurRadius = 12f,
                        ),
                    ),
                    color = MangaLensColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(0.55f),
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MangaLensColors.textPrimary,
                        trackColor = MangaLensColors.fillTrack,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MangaLensColors.textPrimary,
                        modifier = Modifier
                            .background(
                                color = MangaLensColors.black.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutGrid(
    state: HomeState,
    onNavigateToFiles: () -> Unit,
    onNavigateToImages: () -> Unit,
    onNavigateToVault: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeTile(
            icon = Res.drawable.ic_folder_home,
            label = stringResource(Res.string.home_shortcut_files),
            subtitle = stringResource(Res.string.home_shortcut_files_sub),
            iconBackgroundColor = MangaLensColors.tileBlue,
            onClick = onNavigateToFiles,
            modifier = Modifier.weight(1f),
        )
        HomeTile(
            icon = Res.drawable.ic_image_home,
            label = stringResource(Res.string.home_shortcut_images),
            subtitle = stringResource(Res.string.home_shortcut_images_count, state.imageCount),
            iconBackgroundColor = MangaLensColors.tileGreen,
            onClick = onNavigateToImages,
            modifier = Modifier.weight(1f),
        )
        HomeTile(
            icon = Res.drawable.ic_lock_home,
            label = stringResource(Res.string.home_shortcut_vault),
            subtitle = stringResource(Res.string.home_shortcut_vault_sub),
            iconBackgroundColor = MangaLensColors.tilePurple,
            onClick = onNavigateToVault,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RecentSectionHeader(onViewAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.home_recent_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MangaLensColors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.clickable(onClick = onViewAll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_recent_view_all),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MangaLensColors.blueHi,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MangaLensColors.blueHi,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun RecentHorizontalList(
    archives: List<ArchiveFile>,
    coverModels: Map<Long, ViewerPageModel>,
    compact: Boolean,
    onArchiveClick: (Long) -> Unit,
) {
    val itemWidth = if (compact) 54.dp else 86.dp
    val coverHeight = if (compact) 70.dp else 114.dp

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(archives.size, key = { archives[it].id }) { index ->
            val archive = archives[index]
            val progressColor = if (index == 0) MangaLensColors.green else MangaLensColors.primary
            RecentArchiveItem(
                archive = archive,
                coverModel = coverModels[archive.id],
                itemWidth = itemWidth,
                coverHeight = coverHeight,
                progressColor = progressColor,
                onClick = { onArchiveClick(archive.id) },
            )
        }
    }
}

@Composable
private fun RecentArchiveItem(
    archive: ArchiveFile,
    coverModel: ViewerPageModel?,
    itemWidth: Dp,
    coverHeight: Dp,
    progressColor: Color = MangaLensColors.primary,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(itemWidth),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .background(MangaLensColors.surfaceVariant),
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = archive.fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (archive.totalPages > 0) {
                val progress = ((archive.lastReadIndex + 1).toFloat() / archive.totalPages).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(MangaLensColors.fillTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(progressColor),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = archive.fileName,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MangaLensColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyRecentState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MangaLensColors.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MangaLensColors.fillQuaternary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_history_clock),
                    contentDescription = null,
                    tint = MangaLensColors.textTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = stringResource(Res.string.home_recent_empty_title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MangaLensColors.textSecondary,
            )
            Text(
                text = stringResource(Res.string.home_recent_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MangaLensColors.textTertiary,
            )
        }
    }
}
