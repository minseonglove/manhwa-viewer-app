package com.manhwaviewer.presentation.screen.imagegallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.BaseTopBar
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.home_shortcut_images
import manhwa_viewer.presentation.generated.resources.image_gallery_empty
import manhwa_viewer.presentation.generated.resources.image_gallery_total_prefix
import manhwa_viewer.presentation.generated.resources.image_gallery_total_suffix
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImageGalleryScreen(
    onNavigateToViewer: (archiveId: Long, startPage: Int, sortByDate: Boolean, sortAscending: Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: ImageGalleryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is ImageGallerySideEffect.NavigateToViewer -> onNavigateToViewer(effect.archiveId, effect.startPage, effect.sortByDate, effect.sortAscending)
            }
        }
    }

    Scaffold(
        topBar = {
            BaseTopBar(
                title = stringResource(Res.string.home_shortcut_images),
                onBack = onBack,
                sortState = state.sortState,
                onSortFieldChange = { viewModel.onIntent(ImageGalleryIntent.SetSortField(it)) },
                onToggleDirection = { viewModel.onIntent(ImageGalleryIntent.ToggleSortDirection) },
                onToggleViewMode = { viewModel.onIntent(ImageGalleryIntent.ToggleViewMode) },
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
                state.images.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.image_gallery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    if (state.sortState.viewMode == ListViewMode.LIST) {
                        val columns = if (isLandscape) 2 else 1
                        if (columns == 1) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.images, key = { it.id }) { image ->
                                    ImageFileItem(
                                        image = image,
                                        onClick = { viewModel.onIntent(ImageGalleryIntent.OpenImage(image)) },
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
                                items(state.images, key = { it.id }) { image ->
                                    ImageFileItem(
                                        image = image,
                                        onClick = { viewModel.onIntent(ImageGalleryIntent.OpenImage(image)) },
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 96.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.images, key = { it.id }) { image ->
                                ImageFileGridItem(
                                    image = image,
                                    onClick = { viewModel.onIntent(ImageGalleryIntent.OpenImage(image)) },
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = buildAnnotatedString {
                                        append(stringResource(Res.string.image_gallery_total_prefix))
                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                            append("${state.images.size}")
                                        }
                                        append(stringResource(Res.string.image_gallery_total_suffix))
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
