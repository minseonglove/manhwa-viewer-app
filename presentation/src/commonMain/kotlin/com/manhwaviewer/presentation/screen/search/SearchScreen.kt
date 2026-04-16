package com.manhwaviewer.presentation.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.presentation.screen.common.displayName
import com.manhwaviewer.presentation.theme.MangaLensColors
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.file_type_cbr
import manhwa_viewer.presentation.generated.resources.file_type_cbz
import manhwa_viewer.presentation.generated.resources.file_type_folder
import manhwa_viewer.presentation.generated.resources.file_type_folder_with_images
import manhwa_viewer.presentation.generated.resources.file_type_has_images
import manhwa_viewer.presentation.generated.resources.file_type_image
import manhwa_viewer.presentation.generated.resources.file_type_zip
import manhwa_viewer.presentation.generated.resources.ic_archive
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.ic_close_circle
import manhwa_viewer.presentation.generated.resources.ic_file_folder
import manhwa_viewer.presentation.generated.resources.ic_image_gallery
import manhwa_viewer.presentation.generated.resources.ic_search
import manhwa_viewer.presentation.generated.resources.search_cancel
import manhwa_viewer.presentation.generated.resources.search_empty_subtitle
import manhwa_viewer.presentation.generated.resources.search_empty_title
import manhwa_viewer.presentation.generated.resources.search_no_results_subtitle
import manhwa_viewer.presentation.generated.resources.search_no_results_title
import manhwa_viewer.presentation.generated.resources.search_placeholder
import manhwa_viewer.presentation.generated.resources.search_result_count
import manhwa_viewer.presentation.generated.resources.search_section_archives
import manhwa_viewer.presentation.generated.resources.search_section_folders
import manhwa_viewer.presentation.generated.resources.search_section_images
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SearchDividerColor = Color(0x0DFFFFFF)

@Composable
fun SearchScreen(
    onNavigateToViewer: (archiveId: Long, startPage: Int, sortByDate: Boolean, sortAscending: Boolean) -> Unit,
    onNavigateToFileBrowser: (path: String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is SearchSideEffect.NavigateToFileBrowser -> onNavigateToFileBrowser(effect.path)
                is SearchSideEffect.NavigateToViewer -> onNavigateToViewer(
                    effect.archiveId,
                    effect.startPage,
                    effect.sortByDate,
                    effect.sortAscending,
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SearchBar(
            query = state.query,
            onQueryChange = { viewModel.onIntent(SearchIntent.UpdateQuery(it)) },
            onClear = { viewModel.onIntent(SearchIntent.ClearQuery) },
            onCancel = onBack,
            focusRequester = focusRequester,
        )

        val hasQuery = state.query.isNotBlank()
        val hasResults = state.folders.isNotEmpty() || state.archives.isNotEmpty() || state.images.isNotEmpty()

        when {
            state.isLoading && !hasResults -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            hasResults -> {
                SearchResultList(
                    folders = state.folders,
                    archives = state.archives,
                    images = state.images,
                    query = state.query,
                    onFileClick = { viewModel.onIntent(SearchIntent.OpenFile(it)) },
                )
            }
            hasQuery && !hasResults && !state.isLoading -> {
                SearchEmptyState(
                    title = stringResource(Res.string.search_no_results_title),
                    subtitle = stringResource(Res.string.search_no_results_subtitle),
                )
            }
            else -> {
                SearchEmptyState(
                    title = stringResource(Res.string.search_empty_title),
                    subtitle = stringResource(Res.string.search_empty_subtitle),
                )
            }
        }
    }
}

// ── 검색 바 ─────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MangaLensColors.surfaceGlass)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MangaLensColors.fillTertiary)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_search),
                    contentDescription = null,
                    tint = MangaLensColors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MangaLensColors.textTertiary,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MangaLensColors.onSurface,
                        ),
                        cursorBrush = SolidColor(MangaLensColors.blueHi),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = MangaLensColors.textTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onClear() },
                    )
                }
            }
            Text(
                text = stringResource(Res.string.search_cancel),
                style = MaterialTheme.typography.bodyMedium,
                color = MangaLensColors.blueHi,
                modifier = Modifier.clickable { onCancel() },
            )
        }
    }
}

// ── 결과 목록 ────────────────────────────────────────────────────────

@Composable
private fun SearchResultList(
    folders: List<ArchiveFile>,
    archives: List<ArchiveFile>,
    images: List<ArchiveFile>,
    query: String,
    onFileClick: (ArchiveFile) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (folders.isNotEmpty()) {
            item(key = "section_folders") {
                SearchSectionHeader(
                    title = stringResource(Res.string.search_section_folders),
                    count = folders.size,
                )
            }
            itemsIndexed(folders, key = { _, f -> "folder_${f.filePath}" }) { index, file ->
                SearchResultRow(
                    file = file,
                    query = query,
                    isLast = index == folders.lastIndex,
                    onClick = { onFileClick(file) },
                )
            }
        }
        if (archives.isNotEmpty()) {
            item(key = "section_archives") {
                SearchSectionHeader(
                    title = stringResource(Res.string.search_section_archives),
                    count = archives.size,
                )
            }
            itemsIndexed(archives, key = { _, f -> "archive_${f.filePath}" }) { index, file ->
                SearchResultRow(
                    file = file,
                    query = query,
                    isLast = index == archives.lastIndex,
                    onClick = { onFileClick(file) },
                )
            }
        }
        if (images.isNotEmpty()) {
            item(key = "section_images") {
                SearchSectionHeader(
                    title = stringResource(Res.string.search_section_images),
                    count = images.size,
                )
            }
            itemsIndexed(images, key = { _, f -> "image_${f.filePath}/${f.fileName}" }) { index, file ->
                SearchResultRow(
                    file = file,
                    query = query,
                    isLast = index == images.lastIndex,
                    onClick = { onFileClick(file) },
                )
            }
        }
    }
}

// ── 섹션 헤더 ────────────────────────────────────────────────────────

@Composable
private fun SearchSectionHeader(title: String, count: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = MangaLensColors.onSurface,
            )
            Text(
                text = stringResource(Res.string.search_result_count, count),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MangaLensColors.textTertiary,
            )
        }
        HorizontalDivider(color = MangaLensColors.separator)
    }
}

// ── 검색 결과 행 ─────────────────────────────────────────────────────

@Composable
private fun SearchResultRow(
    file: ArchiveFile,
    query: String,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchIconArea(file)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = highlightQuery(file.displayName(), query),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = (-0.224).sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = searchSubtitle(file),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = MangaLensColors.textSecondary,
                )
            }

            if (file.type == ArchiveType.FOLDER) {
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MangaLensColors.separator,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(
                color = SearchDividerColor,
                modifier = Modifier.padding(start = 72.dp),
            )
        }
    }
}

// ── 아이콘 영역 ──────────────────────────────────────────────────────

@Composable
private fun SearchIconArea(file: ArchiveFile) {
    when (file.type) {
        ArchiveType.FOLDER -> {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MangaLensColors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_file_folder),
                    contentDescription = null,
                    tint = MangaLensColors.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        ArchiveType.IMAGE -> {
            AsyncImage(
                model = file.filePath,
                contentDescription = file.fileName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MangaLensColors.archiveIconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_archive),
                    contentDescription = null,
                    tint = MangaLensColors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── 하이라이트 ───────────────────────────────────────────────────────

private fun highlightQuery(text: String, query: String): AnnotatedString {
    val baseStyle = SpanStyle(color = MangaLensColors.onSurface)
    if (query.isBlank()) return buildAnnotatedString {
        withStyle(baseStyle) { append(text) }
    }
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val idx = lowerText.indexOf(lowerQuery)
    if (idx == -1) return buildAnnotatedString {
        withStyle(baseStyle) { append(text) }
    }

    return buildAnnotatedString {
        withStyle(baseStyle) { append(text.substring(0, idx)) }
        withStyle(SpanStyle(color = MangaLensColors.blueHi, fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, idx + query.length))
        }
        withStyle(baseStyle) { append(text.substring(idx + query.length)) }
    }
}

// ── 서브타이틀 ───────────────────────────────────────────────────────

@Composable
private fun searchSubtitle(file: ArchiveFile): String {
    return when (file.type) {
        ArchiveType.FOLDER -> if (file.hasImages) {
            stringResource(Res.string.file_type_folder_with_images)
        } else {
            stringResource(Res.string.file_type_folder)
        }
        ArchiveType.ZIP, ArchiveType.CBZ, ArchiveType.CBR -> {
            val typeName = when (file.type) {
                ArchiveType.CBZ -> stringResource(Res.string.file_type_cbz)
                ArchiveType.CBR -> stringResource(Res.string.file_type_cbr)
                else -> stringResource(Res.string.file_type_zip)
            }
            if (file.hasImages) {
                "$typeName · ${stringResource(Res.string.file_type_has_images)}"
            } else {
                typeName
            }
        }
        ArchiveType.IMAGE -> stringResource(Res.string.file_type_image)
    }
}

// ── 빈 상태 ──────────────────────────────────────────────────────────

@Composable
private fun SearchEmptyState(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MangaLensColors.fillTertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_search),
                    contentDescription = null,
                    tint = MangaLensColors.textTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MangaLensColors.textSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MangaLensColors.textTertiary,
                )
            }
        }
    }
}
