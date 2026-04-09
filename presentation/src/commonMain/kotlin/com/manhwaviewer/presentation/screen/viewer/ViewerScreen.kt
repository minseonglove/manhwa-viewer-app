package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import com.manhwaviewer.presentation.theme.MangaLensColors
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.IntSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.presentation.screen.common.displayName
import com.manhwaviewer.domain.entity.OcrBlock
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.loading_next_volume
import manhwa_viewer.presentation.generated.resources.page_content_description
import manhwa_viewer.presentation.generated.resources.viewer_image_not_found
import manhwa_viewer.presentation.generated.resources.viewer_translation_result_title
import org.jetbrains.compose.resources.stringResource
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.zoomable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val BoundingBoxColor = MangaLensColors.emerald

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ViewerScreen(
    archiveId: Long,
    startPage: Int = 0,
    sortByDate: Boolean = false,
    sortAscending: Boolean = true,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ViewerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(archiveId) {
        viewModel.onIntent(ViewerIntent.LoadArchive(archiveId, startPage, sortByDate, sortAscending))
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onIntent(ViewerIntent.SaveProgress)
        }
    }

    val viewMode = state.preference.viewMode
    val isDoublePage = viewMode == ViewMode.DOUBLE_PAGE

    val hasNextVolume = state.nextVolume != null
    val realPageCount = if (isDoublePage) spreadCount(state.totalPages) else state.totalPages
    val pagerPageCount = realPageCount + (if (hasNextVolume) 1 else 0)

    val pagerState = key(viewMode, state.archive?.id) {
        rememberPagerState(
            initialPage = if (isDoublePage) pageToSpread(state.currentPage) else state.currentPage,
            pageCount = { pagerPageCount },
        )
    }

    // pager → state 동기화: 사용자 스와이프 반영
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settledPage ->
                val actualPage = if (isDoublePage) spreadToFirstPage(settledPage) else settledPage
                viewModel.onIntent(ViewerIntent.SetPage(actualPage))
            }
    }

    // state → pager 동기화: LoadArchive 완료 후 올바른 페이지로 이동
    LaunchedEffect(state.currentPage, state.totalPages) {
        if (state.totalPages > 0) {
            val targetPage = if (isDoublePage) pageToSpread(state.currentPage) else state.currentPage
            if (pagerState.currentPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    val scope = rememberCoroutineScope()

    val viewerBackground = when (state.preference.backgroundColor) {
        BackgroundColor.BLACK -> Color.Black
        BackgroundColor.WHITE -> Color.White
        BackgroundColor.AUTO -> Color.Black // AUTO는 기본 검정 (향후 이미지 분석 기반 결정)
    }

    val hazeState = rememberHazeState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerBackground),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = if (state.preference.backgroundColor == BackgroundColor.WHITE) Color.Black else Color.White,
            )
        } else if (state.totalPages > 0) {
            when (viewMode) {
                ViewMode.WEBTOON -> {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                            .hazeSource(state = hazeState),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = true,
                    ) { page ->
                        if (hasNextVolume && page >= realPageCount) {
                            NextVolumeTransitionPage(nextVolume = state.nextVolume)
                        } else {
                        val entryName = state.imageEntries.getOrNull(page)
                        val pageModel = entryName?.let {
                            ViewerPageModel(archivePath = state.filePath, entryNames = listOf(it))
                        }
                        ZoomableImage(
                            pageModel = pageModel,
                            page = page,
                            keepZoomState = state.preference.keepZoomState,
                            imageScaleMode = state.preference.imageScaleMode,
                            pagerSettledPage = pagerState.settledPage,
                            onTapLeft = {
                                scope.launch {
                                    pagerState.scrollToPage((page - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                scope.launch {
                                    pagerState.scrollToPage((page + 1).coerceAtMost(pagerPageCount - 1))
                                }
                            },
                            cachedBlocks = if (page == state.currentPage) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (page == state.currentPage) state.cachedImageWidth else 0,
                            cachedImageHeight = if (page == state.currentPage) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (page == state.currentPage) state.cachedOverlayColors else emptyList(),
                            selectedCachedBlockIndices = if (page == state.currentPage) state.selectedCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockSelection(it)) },
                            autoOcrBlocks = if (page == state.currentPage) state.autoOcrBlocks else emptyList(),
                            autoOcrImageWidth = if (page == state.currentPage) state.autoOcrImageWidth else 0,
                            autoOcrImageHeight = if (page == state.currentPage) state.autoOcrImageHeight else 0,
                            autoProcessStatus = if (page == state.currentPage) state.autoProcessStatus else AutoProcessStatus.IDLE,
                            selectedOcrBlockIndices = if (page == state.currentPage) state.selectedOcrBlockIndices else emptySet(),
                            onOcrBlockTap = { viewModel.onIntent(ViewerIntent.ToggleOcrBlockSelection(it)) },
                            autoTranslationBlocks = if (page == state.currentPage) state.autoTranslationBlocks else emptyList(),
                            autoTranslationImageWidth = if (page == state.currentPage) state.autoTranslationImageWidth else 0,
                            autoTranslationImageHeight = if (page == state.currentPage) state.autoTranslationImageHeight else 0,
                            autoTranslationOverlayColors = if (page == state.currentPage) state.autoTranslationOverlayColors else emptyList(),
                            globalOverlayEnabled = state.globalOverlayEnabled,
                        )
                        }
                    }
                }

                ViewMode.DOUBLE_PAGE -> {
                    val isRtl = state.preference.readingDirection == ReadingDirection.RTL
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                            .hazeSource(state = hazeState),
                        reverseLayout = isRtl,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = true,
                    ) { spreadIndex ->
                        if (hasNextVolume && spreadIndex >= realPageCount) {
                            NextVolumeTransitionPage(nextVolume = state.nextVolume)
                        } else {
                        val (firstPage, secondPage) = spreadToPages(spreadIndex, state.totalPages)
                        val firstEntry = state.imageEntries.getOrNull(firstPage)
                        val secondEntry = secondPage?.let { state.imageEntries.getOrNull(it) }
                        val pageModel = firstEntry?.let {
                            val entries = if (secondEntry != null) {
                                if (isRtl) listOf(secondEntry, firstEntry) else listOf(firstEntry, secondEntry)
                            } else {
                                listOf(firstEntry)
                            }
                            ViewerPageModel(archivePath = state.filePath, entryNames = entries, isRtl = isRtl)
                        }
                        val isCurrentSpread = firstPage == state.currentPage || secondPage == state.currentPage
                        ZoomableImage(
                            pageModel = pageModel,
                            page = spreadIndex,
                            keepZoomState = state.preference.keepZoomState,
                            imageScaleMode = state.preference.imageScaleMode,
                            pagerSettledPage = pagerState.settledPage,
                            onTapLeft = {
                                scope.launch {
                                    val target = if (isRtl) (spreadIndex + 1).coerceAtMost(pagerPageCount - 1)
                                                 else (spreadIndex - 1).coerceAtLeast(0)
                                    pagerState.scrollToPage(target)
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                scope.launch {
                                    val target = if (isRtl) (spreadIndex - 1).coerceAtLeast(0)
                                                 else (spreadIndex + 1).coerceAtMost(pagerPageCount - 1)
                                    pagerState.scrollToPage(target)
                                }
                            },
                            cachedBlocks = if (isCurrentSpread) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (isCurrentSpread) state.cachedImageWidth else 0,
                            cachedImageHeight = if (isCurrentSpread) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (isCurrentSpread) state.cachedOverlayColors else emptyList(),
                            selectedCachedBlockIndices = if (isCurrentSpread) state.selectedCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockSelection(it)) },
                            autoOcrBlocks = if (isCurrentSpread) state.autoOcrBlocks else emptyList(),
                            autoOcrImageWidth = if (isCurrentSpread) state.autoOcrImageWidth else 0,
                            autoOcrImageHeight = if (isCurrentSpread) state.autoOcrImageHeight else 0,
                            autoProcessStatus = if (isCurrentSpread) state.autoProcessStatus else AutoProcessStatus.IDLE,
                            selectedOcrBlockIndices = if (isCurrentSpread) state.selectedOcrBlockIndices else emptySet(),
                            onOcrBlockTap = { viewModel.onIntent(ViewerIntent.ToggleOcrBlockSelection(it)) },
                            autoTranslationBlocks = if (isCurrentSpread) state.autoTranslationBlocks else emptyList(),
                            autoTranslationImageWidth = if (isCurrentSpread) state.autoTranslationImageWidth else 0,
                            autoTranslationImageHeight = if (isCurrentSpread) state.autoTranslationImageHeight else 0,
                            autoTranslationOverlayColors = if (isCurrentSpread) state.autoTranslationOverlayColors else emptyList(),
                            globalOverlayEnabled = state.globalOverlayEnabled,
                        )
                        }
                    }
                }

                ViewMode.SINGLE_PAGE -> {
                    val isSinglePageRtl = state.preference.readingDirection == ReadingDirection.RTL
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                            .hazeSource(state = hazeState),
                        reverseLayout = isSinglePageRtl,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = true,
                    ) { page ->
                        if (hasNextVolume && page >= realPageCount) {
                            NextVolumeTransitionPage(nextVolume = state.nextVolume)
                        } else {
                        val entryName = state.imageEntries.getOrNull(page)
                        val pageModel = entryName?.let {
                            ViewerPageModel(archivePath = state.filePath, entryNames = listOf(it))
                        }
                        ZoomableImage(
                            pageModel = pageModel,
                            page = page,
                            keepZoomState = state.preference.keepZoomState,
                            imageScaleMode = state.preference.imageScaleMode,
                            pagerSettledPage = pagerState.settledPage,
                            onTapLeft = {
                                scope.launch {
                                    val target = if (isSinglePageRtl) (page + 1).coerceAtMost(pagerPageCount - 1)
                                                 else (page - 1).coerceAtLeast(0)
                                    pagerState.scrollToPage(target)
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                scope.launch {
                                    val target = if (isSinglePageRtl) (page - 1).coerceAtLeast(0)
                                                 else (page + 1).coerceAtMost(pagerPageCount - 1)
                                    pagerState.scrollToPage(target)
                                }
                            },
                            cachedBlocks = if (page == state.currentPage) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (page == state.currentPage) state.cachedImageWidth else 0,
                            cachedImageHeight = if (page == state.currentPage) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (page == state.currentPage) state.cachedOverlayColors else emptyList(),
                            selectedCachedBlockIndices = if (page == state.currentPage) state.selectedCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockSelection(it)) },
                            autoOcrBlocks = if (page == state.currentPage) state.autoOcrBlocks else emptyList(),
                            autoOcrImageWidth = if (page == state.currentPage) state.autoOcrImageWidth else 0,
                            autoOcrImageHeight = if (page == state.currentPage) state.autoOcrImageHeight else 0,
                            autoProcessStatus = if (page == state.currentPage) state.autoProcessStatus else AutoProcessStatus.IDLE,
                            selectedOcrBlockIndices = if (page == state.currentPage) state.selectedOcrBlockIndices else emptySet(),
                            onOcrBlockTap = { viewModel.onIntent(ViewerIntent.ToggleOcrBlockSelection(it)) },
                            autoTranslationBlocks = if (page == state.currentPage) state.autoTranslationBlocks else emptyList(),
                            autoTranslationImageWidth = if (page == state.currentPage) state.autoTranslationImageWidth else 0,
                            autoTranslationImageHeight = if (page == state.currentPage) state.autoTranslationImageHeight else 0,
                            autoTranslationOverlayColors = if (page == state.currentPage) state.autoTranslationOverlayColors else emptyList(),
                            globalOverlayEnabled = state.globalOverlayEnabled,
                        )
                        }
                    }
                }
            }
        } else {
            Text(
                text = stringResource(Res.string.viewer_image_not_found),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 상단 툴바
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopToolbar(
                title = state.archive?.displayName() ?: "",
                showSelectTranslateButton = state.hasSelectedBlocks,
                isOverlayEnabled = state.globalOverlayEnabled,
                isOverlayButtonEnabled = state.isOverlayButtonEnabled,
                onBack = {
                    viewModel.onIntent(ViewerIntent.SaveProgress)
                    onBack()
                },
                onTranslateSelected = { viewModel.onIntent(ViewerIntent.TranslateSelectedBlocks) },
                onToggleOverlay = { viewModel.onIntent(ViewerIntent.ToggleGlobalOverlay) },
            )
        }

        // 하단 패널
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewerBottomPanel(
                hazeState = hazeState,
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                isDoublePage = isDoublePage,
                pagerPageCount = pagerPageCount,
                currentPagerPage = pagerState.currentPage,
                readingDirection = state.preference.readingDirection,
                viewMode = viewMode,
                imageScaleMode = state.preference.imageScaleMode,
                autoProcessMode = state.autoProcessMode,
                autoProcessStatus = state.autoProcessStatus,
                pipelineStage = state.pipelineStage,
                hasCachedTranslation = state.autoTranslationBlocks.isNotEmpty() || state.cachedBlocks.isNotEmpty(),
                hasCachedOcr = state.autoOcrBlocks.isNotEmpty(),
                selectedTab = state.selectedBottomTab,
                isLastPage = state.isLastPage,
                nextVolumeName = state.nextVolume?.fileName,
                nextVolumeThumbnailModel = state.nextVolume?.let { nv ->
                    nv.firstEntryName?.let { ViewerPageModel(archivePath = nv.filePath, entryNames = listOf(it)) }
                },
                onPageChange = { page ->
                    scope.launch { pagerState.scrollToPage(page) }
                },
                onSetReadingDirection = { viewModel.onIntent(ViewerIntent.SetReadingDirection(it)) },
                onSetViewMode = { viewModel.onIntent(ViewerIntent.SetViewMode(it)) },
                onSetImageScaleMode = { viewModel.onIntent(ViewerIntent.SetImageScaleMode(it)) },
                onSetAutoProcessMode = { viewModel.onIntent(ViewerIntent.SetAutoProcessMode(it)) },
                onRetryAutoProcess = { viewModel.onIntent(ViewerIntent.RetryAutoProcess) },
                onTabSelected = { viewModel.onIntent(ViewerIntent.SetBottomTab(it)) },
                onNavigateToNextVolume = { viewModel.onIntent(ViewerIntent.NavigateToNextVolume) },
            )
        }

        // 바텀시트 표시 중 뒤로가기 → 바텀시트만 닫기
        BackHandler(enabled = state.isSelectedTranslationSheetVisible) {
            viewModel.onIntent(ViewerIntent.DismissSelectedTranslation)
        }

        // 선택 번역 바텀시트 외부 터치 감지 (dismiss) — 바텀시트보다 먼저 배치
        if (state.isSelectedTranslationSheetVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            viewModel.onIntent(ViewerIntent.DismissSelectedTranslation)
                        }
                    },
            )
        }

        // 선택 번역 바텀시트
        AnimatedVisibility(
            visible = state.isSelectedTranslationSheetVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SelectedTranslationBottomSheet(
                translations = state.selectedBlockTranslations,
                isTranslating = state.isSelectedTranslating,
                onDismiss = { viewModel.onIntent(ViewerIntent.DismissSelectedTranslation) },
                hazeState = hazeState,
            )
        }
    }
}


// ── ZoomableImage (telephoto + keepZoomState + 크롭 오버레이 + BB 하이라이트) ──

@Composable
private fun ZoomableImage(
    pageModel: ViewerPageModel?,
    page: Int,
    keepZoomState: Boolean,
    imageScaleMode: ImageScaleMode,
    pagerSettledPage: Int,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
    cachedBlocks: List<TranslationBlock>,
    cachedImageWidth: Int,
    cachedImageHeight: Int,
    cachedOverlayColors: List<androidx.compose.ui.graphics.Color>,
    selectedCachedBlockIndices: Set<Int>,
    onCachedBlockTap: (Int) -> Unit,
    autoOcrBlocks: List<OcrBlock>,
    autoOcrImageWidth: Int,
    autoOcrImageHeight: Int,
    autoProcessStatus: AutoProcessStatus,
    selectedOcrBlockIndices: Set<Int>,
    onOcrBlockTap: (Int) -> Unit,
    autoTranslationBlocks: List<TranslationBlock>,
    autoTranslationImageWidth: Int,
    autoTranslationImageHeight: Int,
    autoTranslationOverlayColors: List<androidx.compose.ui.graphics.Color>,
    globalOverlayEnabled: Boolean,
) {
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 5f),
    )
    var baseZoomFactor by remember { mutableStateOf(1f) }

    // 항상 Fit으로 고정 — 확대는 zoomTo(baseZoom)로 처리
    LaunchedEffect(Unit) {
        zoomableState.contentScale = ContentScale.Fit
    }

    // Reset zoom when page scrolls off-screen (unless keepZoomState is enabled)
    if (!keepZoomState && pagerSettledPage != page) {
        LaunchedEffect(Unit) {
            if (baseZoomFactor > 1f) {
                zoomableState.zoomTo(baseZoomFactor, animationSpec = snap())
            } else {
                zoomableState.resetZoom(animationSpec = snap())
            }
        }
    }


    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // onClick 람다가 telephoto 내부에서 캐싱되므로, 최신 값을 항상 참조하도록 rememberUpdatedState 사용
    val currentCachedBlocks by rememberUpdatedState(cachedBlocks)
    val currentCachedImageWidth by rememberUpdatedState(cachedImageWidth)
    val currentCachedImageHeight by rememberUpdatedState(cachedImageHeight)
    val currentOnCachedBlockTap by rememberUpdatedState(onCachedBlockTap)
    val currentAutoOcrBlocks by rememberUpdatedState(autoOcrBlocks)
    val currentAutoOcrImageWidth by rememberUpdatedState(autoOcrImageWidth)
    val currentAutoOcrImageHeight by rememberUpdatedState(autoOcrImageHeight)
    val currentAutoProcessStatus by rememberUpdatedState(autoProcessStatus)
    val currentOnOcrBlockTap by rememberUpdatedState(onOcrBlockTap)

    // SubSamplingImage는 단일 페이지에서 사용 (오버레이 유무 무관)
    // (양면 모드는 두 이미지 합성이 필요하므로 기존 Coil Fetcher 경로 유지)
    val useSubSampling = pageModel != null && pageModel.entryNames.size == 1

    // subSamplingFailed 상태를 이 스코프에서 관리 (fallback 용)
    var subSamplingFailed by remember { mutableStateOf(false) }

    if (useSubSampling && !subSamplingFailed && pageModel != null) {
        // SubSamplingImage 경로: 보이는 영역만 타일 디코딩 (메모리 안전)
        val getImageBytesUseCase: GetImageBytesUseCase = koinInject()
        var sourceResult by remember { mutableStateOf<ArchiveImageSourceResult?>(null) }

        LaunchedEffect(pageModel) {
            try {
                sourceResult = createArchiveImageSource(
                    archivePath = pageModel.archivePath,
                    entryName = pageModel.entryNames[0],
                    getImageBytesUseCase = getImageBytesUseCase,
                )
                subSamplingFailed = false
            } catch (_: Exception) {
                subSamplingFailed = true
            }
        }

        // baseZoom 계산 및 적용 (setContentLocation은 SubSamplingImage가 내부 관리)
        val result = sourceResult
        var subSamplingReady by remember { mutableStateOf(false) }
        LaunchedEffect(result, imageScaleMode, containerSize) {
            if (result == null || containerSize == IntSize.Zero) return@LaunchedEffect
            val zoom = calculateBaseZoom(
                imageScaleMode, result.imageSize.width, result.imageSize.height,
                containerSize.width, containerSize.height,
            )
            baseZoomFactor = zoom
            zoomableState.resetZoom(animationSpec = snap())
            if (zoom > 1f) {
                zoomableState.zoomTo(zoom, animationSpec = snap())
            }
            subSamplingReady = true
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .zoomable(
                        state = zoomableState,
                        gestures = EnabledZoomGestures.ZoomAndPan,
                        onClick = { offset ->
                            var handled = false
                            val tf = zoomableState.contentTransformation
                            val originPxX = tf.transformOrigin.pivotFractionX * containerSize.width
                            val originPxY = tf.transformOrigin.pivotFractionY * containerSize.height
                            val imgX = (offset.x - tf.offset.x - originPxX) / tf.scale.scaleX + originPxX
                            val imgY = (offset.y - tf.offset.y - originPxY) / tf.scale.scaleY + originPxY
                            // 자동 OCR 완료 상태: OCR 블록 바운딩 박스 토글
                            if (!handled && (currentAutoProcessStatus == AutoProcessStatus.OCR_DONE || currentAutoProcessStatus == AutoProcessStatus.TRANSLATE_DONE) &&
                                currentAutoOcrBlocks.isNotEmpty() && currentAutoOcrImageWidth > 0) {
                                val hitIdx = currentAutoOcrBlocks.indexOfFirst { b ->
                                    imgX in b.x.toFloat()..(b.x + b.width).toFloat() &&
                                        imgY in b.y.toFloat()..(b.y + b.height).toFloat()
                                }
                                if (hitIdx >= 0) {
                                    currentOnOcrBlockTap(hitIdx)
                                    handled = true
                                }
                            }

                            // 캐시된 블록 탭 (자동 OCR OFF일 때)
                            if (!handled && currentCachedBlocks.isNotEmpty() && currentCachedImageWidth > 0 && currentCachedImageHeight > 0) {
                                val hitIdx = currentCachedBlocks.indexOfFirst { b ->
                                    imgX in b.x.toFloat()..(b.x + b.width).toFloat() &&
                                        imgY in b.y.toFloat()..(b.y + b.height).toFloat()
                                }
                                if (hitIdx >= 0) {
                                    currentOnCachedBlockTap(hitIdx)
                                    handled = true
                                }
                            }
                            if (!handled) {
                                when {
                                    offset.x < containerSize.width * 0.2f -> onTapLeft()
                                    offset.x > containerSize.width * 0.8f -> onTapRight()
                                    else -> onTapCenter()
                                }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (result != null) {
                    val imageState = rememberSubSamplingImageState(
                        imageSource = result.source,
                        zoomableState = zoomableState,
                    )

                    SubSamplingImage(
                        state = imageState,
                        contentDescription = stringResource(Res.string.page_content_description, page + 1),
                        modifier = Modifier.fillMaxSize(),
                        // baseZoom 적용 전까지 투명하게 유지하여 깜빡임 방지
                        alpha = if (subSamplingReady || baseZoomFactor <= 1f) 1f else 0f,
                    )

                    // SubSamplingImage가 autoApplyTransformations=false로 설정하므로
                    // 오버레이에 줌/팬 변환을 수동으로 적용해야 함
                    fun overlayZoomModifier(imgW: Int, imgH: Int) = Modifier.fillMaxSize().graphicsLayer {
                        val transform = zoomableState.contentTransformation
                        if (transform.isSpecified && containerSize != IntSize.Zero && imgW > 0 && imgH > 0) {
                            val fitScale = minOf(
                                containerSize.width.toFloat() / imgW,
                                containerSize.height.toFloat() / imgH,
                            )
                            if (fitScale > 0f) {
                                val zoomX = transform.scale.scaleX / fitScale
                                val zoomY = transform.scale.scaleY / fitScale
                                val fitOffsetX = (containerSize.width - imgW * fitScale) / 2f
                                val fitOffsetY = (containerSize.height - imgH * fitScale) / 2f

                                scaleX = zoomX
                                scaleY = zoomY
                                translationX = transform.offset.x - fitOffsetX * zoomX
                                translationY = transform.offset.y - fitOffsetY * zoomY
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                    }


                    // 캐시된 말풍선
                    if (cachedBlocks.isNotEmpty()) {
                        if (globalOverlayEnabled) {
                            // 눈 아이콘 ON: 전체 블록 번역 오버레이 표시
                            val (visibleTextBlocks, visibleTranslations, visibleColors) = remember(
                                cachedBlocks, cachedOverlayColors,
                            ) {
                                Triple(
                                    cachedBlocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) },
                                    cachedBlocks.map { TranslationResult(it.originalText, it.translatedText) },
                                    cachedOverlayColors,
                                )
                            }
                            TranslationOverlay(
                                textBlocks = visibleTextBlocks,
                                translationResults = visibleTranslations,
                                dominantColors = visibleColors,
                                imageWidth = cachedImageWidth,
                                imageHeight = cachedImageHeight,
                                imageScaleMode = ImageScaleMode.AUTO_FIT,
                                modifier = overlayZoomModifier(cachedImageWidth, cachedImageHeight),
                            )
                        } else if (selectedCachedBlockIndices.isNotEmpty()) {
                            // 개별 탭: 바운딩 박스만 표시
                            val selectedTextBlocks = remember(selectedCachedBlockIndices, cachedBlocks) {
                                selectedCachedBlockIndices.sorted().mapNotNull { i ->
                                    cachedBlocks.getOrNull(i)?.let {
                                        TextBlock(it.x, it.y, it.width, it.height, it.originalText)
                                    }
                                }
                            }
                            BoundingBoxDisplayOverlay(
                                textBlocks = selectedTextBlocks,
                                imageWidth = cachedImageWidth,
                                imageHeight = cachedImageHeight,
                                imageScaleMode = ImageScaleMode.AUTO_FIT,
                                modifier = overlayZoomModifier(cachedImageWidth, cachedImageHeight),
                            )
                        }
                    }

                    // 자동 번역 완료 시 오버레이 (globalOverlayEnabled + 캐시 블록 없을 때만 — 이중 렌더링 방지)
                    if (globalOverlayEnabled && autoTranslationBlocks.isNotEmpty() && cachedBlocks.isEmpty()) {
                        val (autoTextBlocks, autoTranslations, autoColors) = remember(
                            autoTranslationBlocks, autoTranslationOverlayColors,
                        ) {
                            Triple(
                                autoTranslationBlocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) },
                                autoTranslationBlocks.map { TranslationResult(it.originalText, it.translatedText) },
                                autoTranslationOverlayColors,
                            )
                        }
                        TranslationOverlay(
                            textBlocks = autoTextBlocks,
                            translationResults = autoTranslations,
                            dominantColors = autoColors,
                            imageWidth = autoTranslationImageWidth,
                            imageHeight = autoTranslationImageHeight,
                            imageScaleMode = ImageScaleMode.AUTO_FIT,
                            modifier = overlayZoomModifier(autoTranslationImageWidth, autoTranslationImageHeight),
                        )
                    }

                    // 자동 OCR 완료 시 선택된 블록 바운딩 박스 (터치 비소비)
                    if ((autoProcessStatus == AutoProcessStatus.OCR_DONE || autoProcessStatus == AutoProcessStatus.TRANSLATE_DONE) && selectedOcrBlockIndices.isNotEmpty() && autoOcrBlocks.isNotEmpty()) {
                        val selectedTextBlocks = remember(selectedOcrBlockIndices, autoOcrBlocks) {
                            selectedOcrBlockIndices.sorted().mapNotNull { i ->
                                autoOcrBlocks.getOrNull(i)?.let {
                                    TextBlock(it.x, it.y, it.width, it.height, it.text)
                                }
                            }
                        }
                        BoundingBoxDisplayOverlay(
                            textBlocks = selectedTextBlocks,
                            imageWidth = autoOcrImageWidth,
                            imageHeight = autoOcrImageHeight,
                            imageScaleMode = ImageScaleMode.AUTO_FIT,
                            modifier = overlayZoomModifier(autoOcrImageWidth, autoOcrImageHeight),
                        )
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    } else {
        // Coil Image+painter 경로: 양면 모드 (두 이미지 합성) 전용
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .zoomable(
                        state = zoomableState,
                        gestures = EnabledZoomGestures.ZoomAndPan,
                        onClick = { offset ->
                            var handled = false
                            val tf = zoomableState.contentTransformation
                            val originPxX = tf.transformOrigin.pivotFractionX * containerSize.width
                            val originPxY = tf.transformOrigin.pivotFractionY * containerSize.height
                            val localX = (offset.x - tf.offset.x - originPxX) / tf.scale.scaleX + originPxX
                            val localY = (offset.y - tf.offset.y - originPxY) / tf.scale.scaleY + originPxY
                            // 자동 OCR 완료 상태: OCR 블록 바운딩 박스 토글
                            if (!handled && (currentAutoProcessStatus == AutoProcessStatus.OCR_DONE || currentAutoProcessStatus == AutoProcessStatus.TRANSLATE_DONE) &&
                                currentAutoOcrBlocks.isNotEmpty() && currentAutoOcrImageWidth > 0) {
                                val (fitScale, fitOffset) = calculateImageTransform(
                                    ImageScaleMode.AUTO_FIT,
                                    containerSize.width.toFloat(), containerSize.height.toFloat(),
                                    currentAutoOcrImageWidth.toFloat(), currentAutoOcrImageHeight.toFloat(),
                                )
                                val hitX = (localX - fitOffset.x) / fitScale
                                val hitY = (localY - fitOffset.y) / fitScale
                                val hitIdx = currentAutoOcrBlocks.indexOfFirst { b ->
                                    hitX in b.x.toFloat()..(b.x + b.width).toFloat() &&
                                        hitY in b.y.toFloat()..(b.y + b.height).toFloat()
                                }
                                if (hitIdx >= 0) {
                                    currentOnOcrBlockTap(hitIdx)
                                    handled = true
                                }
                            }

                            // 캐시된 블록 탭 (자동 OCR OFF일 때)
                            if (!handled && currentCachedBlocks.isNotEmpty() && currentCachedImageWidth > 0 && currentCachedImageHeight > 0) {
                                val (fitScale, fitOffset) = calculateImageTransform(
                                    ImageScaleMode.AUTO_FIT,
                                    containerSize.width.toFloat(), containerSize.height.toFloat(),
                                    currentCachedImageWidth.toFloat(), currentCachedImageHeight.toFloat(),
                                )
                                val hitX = (localX - fitOffset.x) / fitScale
                                val hitY = (localY - fitOffset.y) / fitScale
                                val hitIdx = currentCachedBlocks.indexOfFirst { b ->
                                    hitX in b.x.toFloat()..(b.x + b.width).toFloat() &&
                                        hitY in b.y.toFloat()..(b.y + b.height).toFloat()
                                }
                                if (hitIdx >= 0) {
                                    currentOnCachedBlockTap(hitIdx)
                                    handled = true
                                }
                            }
                            if (!handled) {
                                when {
                                    offset.x < containerSize.width * 0.2f -> onTapLeft()
                                    offset.x > containerSize.width * 0.8f -> onTapRight()
                                    else -> onTapCenter()
                                }
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (pageModel != null) {
                    // Coil 디코딩은 원래 scaleMode 해상도로, 렌더링은 Fit으로
                    val decodeScale = imageScaleMode.toContentScale()
                    val renderScale = ContentScale.Fit
                    var coilReady by remember { mutableStateOf(false) }

                    val painter = rememberAsyncImagePainter(
                        model = pageModel,
                        contentScale = decodeScale,
                    )

                    Image(
                        painter = painter,
                        contentDescription = stringResource(Res.string.page_content_description, page + 1),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = renderScale,
                        // baseZoom 적용 전까지 투명하게 유지하여 깜빡임 방지
                        alpha = if (coilReady || baseZoomFactor <= 1f) 1f else 0f,
                    )

                    val painterState = painter.state.collectAsState().value
                    when (painterState) {
                        is AsyncImagePainter.State.Loading -> {
                            CircularProgressIndicator(color = Color.White)
                        }
                        is AsyncImagePainter.State.Success -> {
                            val imgWidth = painterState.result.image.width
                            val imgHeight = painterState.result.image.height

                            // 콘텐츠 위치 설정 → 줌 계산/적용을 단일 Effect에서 순차 실행 (레이스 컨디션 방지)
                            LaunchedEffect(imgWidth, imgHeight, imageScaleMode, containerSize) {
                                zoomableState.setContentLocation(
                                    ZoomableContentLocation.scaledInsideAndCenterAligned(
                                        Size(imgWidth.toFloat(), imgHeight.toFloat())
                                    )
                                )
                                if (containerSize == IntSize.Zero) return@LaunchedEffect
                                val zoom = calculateBaseZoom(
                                    imageScaleMode, imgWidth, imgHeight,
                                    containerSize.width, containerSize.height,
                                )
                                baseZoomFactor = zoom
                                zoomableState.resetZoom(animationSpec = snap())
                                if (zoom > 1f) {
                                    zoomableState.zoomTo(zoom, animationSpec = snap())
                                }
                                coilReady = true
                            }

                            // 캐시된 말풍선
                            if (cachedBlocks.isNotEmpty()) {
                                if (globalOverlayEnabled) {
                                    // 눈 아이콘 ON: 전체 블록 번역 오버레이 표시
                                    val (visibleTextBlocks, visibleTranslations, visibleColors) = remember(
                                        cachedBlocks, cachedOverlayColors,
                                    ) {
                                        Triple(
                                            cachedBlocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) },
                                            cachedBlocks.map { TranslationResult(it.originalText, it.translatedText) },
                                            cachedOverlayColors,
                                        )
                                    }
                                    TranslationOverlay(
                                        textBlocks = visibleTextBlocks,
                                        translationResults = visibleTranslations,
                                        dominantColors = visibleColors,
                                        imageWidth = cachedImageWidth,
                                        imageHeight = cachedImageHeight,
                                        imageScaleMode = ImageScaleMode.AUTO_FIT,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else if (selectedCachedBlockIndices.isNotEmpty()) {
                                    // 개별 탭: 바운딩 박스만 표시
                                    val selectedTextBlocks = remember(selectedCachedBlockIndices, cachedBlocks) {
                                        selectedCachedBlockIndices.sorted().mapNotNull { i ->
                                            cachedBlocks.getOrNull(i)?.let {
                                                TextBlock(it.x, it.y, it.width, it.height, it.originalText)
                                            }
                                        }
                                    }
                                    BoundingBoxDisplayOverlay(
                                        textBlocks = selectedTextBlocks,
                                        imageWidth = cachedImageWidth,
                                        imageHeight = cachedImageHeight,
                                        imageScaleMode = ImageScaleMode.AUTO_FIT,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }

                            // 자동 번역 완료 시 오버레이 (globalOverlayEnabled + 캐시 블록 없을 때만 — 이중 렌더링 방지)
                            if (globalOverlayEnabled && autoTranslationBlocks.isNotEmpty() && cachedBlocks.isEmpty()) {
                                val (autoTextBlocks, autoTranslations, autoColors) = remember(
                                    autoTranslationBlocks, autoTranslationOverlayColors,
                                ) {
                                    Triple(
                                        autoTranslationBlocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) },
                                        autoTranslationBlocks.map { TranslationResult(it.originalText, it.translatedText) },
                                        autoTranslationOverlayColors,
                                    )
                                }
                                TranslationOverlay(
                                    textBlocks = autoTextBlocks,
                                    translationResults = autoTranslations,
                                    dominantColors = autoColors,
                                    imageWidth = autoTranslationImageWidth,
                                    imageHeight = autoTranslationImageHeight,
                                    imageScaleMode = ImageScaleMode.AUTO_FIT,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // 자동 OCR 완료 시 선택된 블록 바운딩 박스 (터치 비소비)
                            if ((autoProcessStatus == AutoProcessStatus.OCR_DONE || autoProcessStatus == AutoProcessStatus.TRANSLATE_DONE) && selectedOcrBlockIndices.isNotEmpty() && autoOcrBlocks.isNotEmpty()) {
                                val selectedTextBlocks = remember(selectedOcrBlockIndices, autoOcrBlocks) {
                                    selectedOcrBlockIndices.sorted().mapNotNull { i ->
                                        autoOcrBlocks.getOrNull(i)?.let {
                                            TextBlock(it.x, it.y, it.width, it.height, it.text)
                                        }
                                    }
                                }
                                BoundingBoxDisplayOverlay(
                                    textBlocks = selectedTextBlocks,
                                    imageWidth = autoOcrImageWidth,
                                    imageHeight = autoOcrImageHeight,
                                    imageScaleMode = ImageScaleMode.AUTO_FIT,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        else -> {}
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

// ── Bounding Box 오버레이 ─────────────────────────────────────

@Composable
private fun BoundingBoxOverlay(
    textBlocks: List<TextBlock>,
    imageWidth: Int,
    imageHeight: Int,
    imageScaleMode: ImageScaleMode,
    onBlockTap: (TextBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)

    Canvas(
        modifier = modifier
            .pointerInput(textBlocks, imageWidth, imageHeight, imageScaleMode) {
                detectTapGestures { offset ->
                    val (scale, transformOffset) = calculateImageTransform(
                        imageScaleMode, size.width.toFloat(), size.height.toFloat(),
                        imageWidth.toFloat(), imageHeight.toFloat(),
                    )

                    textBlocks.firstOrNull { block ->
                        val lx = block.x * scale + transformOffset.x
                        val ly = block.y * scale + transformOffset.y
                        val lw = block.width * scale
                        val lh = block.height * scale
                        offset.x in lx..(lx + lw) && offset.y in ly..(ly + lh)
                    }?.let { onBlockTap(it) }
                }
            },
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val (scale, transformOffset) = calculateImageTransform(
            imageScaleMode, size.width, size.height,
            imageWidth.toFloat(), imageHeight.toFloat(),
        )

        textBlocks.forEach { block ->
            drawRect(
                color = BoundingBoxColor,
                topLeft = Offset(
                    x = block.x * scale + transformOffset.x,
                    y = block.y * scale + transformOffset.y,
                ),
                size = Size(
                    width = block.width * scale,
                    height = block.height * scale,
                ),
                style = Stroke(
                    width = 2f,
                    pathEffect = dashEffect,
                ),
            )
        }
    }
}

/** 바운딩 박스를 그리기만 하고 터치를 소비하지 않는 오버레이. */
@Composable
private fun BoundingBoxDisplayOverlay(
    textBlocks: List<TextBlock>,
    imageWidth: Int,
    imageHeight: Int,
    imageScaleMode: ImageScaleMode,
    modifier: Modifier = Modifier,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)

    Canvas(modifier = modifier) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        val (scale, transformOffset) = calculateImageTransform(
            imageScaleMode, size.width, size.height,
            imageWidth.toFloat(), imageHeight.toFloat(),
        )

        textBlocks.forEach { block ->
            drawRect(
                color = BoundingBoxColor,
                topLeft = Offset(
                    x = block.x * scale + transformOffset.x,
                    y = block.y * scale + transformOffset.y,
                ),
                size = Size(
                    width = block.width * scale,
                    height = block.height * scale,
                ),
                style = Stroke(
                    width = 2f,
                    pathEffect = dashEffect,
                ),
            )
        }
    }
}


// ── 이미지 비율 모드 ─────────────────────────────────────────────

/**
 * ContentScale.Fit 대비 추가 확대 배율 계산.
 * extraZoom < 1이면 1f로 클램프 (Fit보다 축소 방지).
 */
private fun calculateBaseZoom(
    imageScaleMode: ImageScaleMode,
    imgWidth: Int, imgHeight: Int,
    containerWidth: Int, containerHeight: Int,
): Float {
    if (containerWidth <= 0 || containerHeight <= 0 || imgWidth <= 0 || imgHeight <= 0) return 1f
    val fitScale = minOf(
        containerWidth.toFloat() / imgWidth,
        containerHeight.toFloat() / imgHeight,
    )
    val targetScale = when (imageScaleMode) {
        ImageScaleMode.AUTO_FIT -> fitScale
        ImageScaleMode.FIT_WIDTH -> containerWidth.toFloat() / imgWidth
        ImageScaleMode.FIT_HEIGHT -> containerHeight.toFloat() / imgHeight
        ImageScaleMode.ORIGINAL -> 1f
    }
    return (targetScale / fitScale).coerceAtLeast(1f)
}

private fun ImageScaleMode.toContentScale(): ContentScale = when (this) {
    ImageScaleMode.FIT_WIDTH -> ContentScale.FillWidth
    ImageScaleMode.FIT_HEIGHT -> ContentScale.FillHeight
    ImageScaleMode.AUTO_FIT -> ContentScale.Fit
    ImageScaleMode.ORIGINAL -> ContentScale.None
}


// ── 선택 번역 바텀시트 ──────────────────────────────────────────

@Composable
private fun SelectedTranslationBottomSheet(
    translations: List<Pair<String, String>>,
    isTranslating: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState,
) {
    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 80.dp.toPx() }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .graphicsLayer { translationY = animatedOffsetY }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (dragOffsetY > dismissThreshold) {
                            onDismiss()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f },
                )
            }
            .clip(sheetShape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MangaLensColors.surface1,
                    tints = listOf(HazeTint(MangaLensColors.surfaceGlass)),
                    blurRadius = 28.dp,
                    noiseFactor = 0.12f,
                ),
            )
            .border(0.5.dp, MangaLensColors.separator, sheetShape)
            .pointerInput(Unit) { detectTapGestures { /* 터치 소비 — 외부 dismiss 방지 */ } }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // 드래그 핸들
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .background(MangaLensColors.fillSecondary, RoundedCornerShape(3.dp)),
            )
        }

        // "번역 결과" 타이틀
        Text(
            text = stringResource(Res.string.viewer_translation_result_title),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        )

        // 타이틀 하단 separator
        HorizontalDivider(
            color = MangaLensColors.separator,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (isTranslating) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            ) {
                translations.forEachIndexed { index, (original, translated) ->
                    Column(modifier = Modifier.padding(vertical = 11.dp)) {
                        Text(
                            text = original,
                            color = MangaLensColors.textSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Text(
                            text = translated,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                        )
                    }
                    if (index < translations.lastIndex) {
                        HorizontalDivider(
                            color = MangaLensColors.separator,
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextVolumeTransitionPage(nextVolume: NextVolumeInfo?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (nextVolume != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (nextVolume.firstEntryName != null) {
                    AsyncImage(
                        model = ViewerPageModel(
                            archivePath = nextVolume.filePath,
                            entryNames = listOf(nextVolume.firstEntryName),
                        ),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = nextVolume.fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White.copy(alpha = 0.6f),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(Res.string.loading_next_volume),
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
