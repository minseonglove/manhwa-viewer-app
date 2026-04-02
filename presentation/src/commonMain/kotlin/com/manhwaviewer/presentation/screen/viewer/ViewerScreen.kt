package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.BackgroundColor
import com.manhwaviewer.presentation.screen.common.displayName
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.entity.ViewMode
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

private val OcrButtonColor = Color(0xFF4A90D9)
private val OverlayBackground = Color(0xB3000000) // rgba(0,0,0,0.7)
private val TranslateBarBackground = Color(0x80000000) // rgba(0,0,0,0.5)
private val AreaSelectBg = Color(0x33F59E0B) // rgba(245,158,11,0.2)
private val AreaSelectBorder = Color(0xFFF59E0B)
private val AreaSelectText = Color(0xFFF59E0B)
private val PageTranslateBg = Color(0x2610B981) // rgba(16,185,129,0.15)
private val PageTranslateBorder = Color(0x4D10B981) // rgba(16,185,129,0.3)
private val PageTranslateText = Color(0xFF10B981)
private val BoundingBoxColor = Color(0xFF10B981)
private val PipelineGreen = Color(0xFF10B981)
private val PipelineOrange = Color(0xFFF59E0B)

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
    var showTranslationOptions by remember { mutableStateOf(false) }

    BackHandler(enabled = state.translationMode != TranslationMode.NONE || showTranslationOptions) {
        if (state.translationMode != TranslationMode.NONE) {
            viewModel.onIntent(ViewerIntent.SetTranslationMode(TranslationMode.NONE))
        }
        showTranslationOptions = false
    }

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

    val pagerPageCount = if (isDoublePage) spreadCount(state.totalPages) else state.totalPages

    val pagerState = rememberPagerState(
        initialPage = if (isDoublePage) pageToSpread(state.currentPage) else state.currentPage,
        pageCount = { pagerPageCount },
    )

    val coroutineScope = rememberCoroutineScope()

    // pager → state 단방향 동기화: pagerState가 단일 진실의 원천
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settledPage ->
                val actualPage = if (isDoublePage) spreadToFirstPage(settledPage) else settledPage
                viewModel.onIntent(ViewerIntent.SetPage(actualPage))
            }
    }

    var viewerContainerSize by remember { mutableStateOf(IntSize.Zero) }

    val viewerBackground = when (state.preference.backgroundColor) {
        BackgroundColor.BLACK -> Color.Black
        BackgroundColor.WHITE -> Color.White
        BackgroundColor.AUTO -> Color.Black // AUTO는 기본 검정 (향후 이미지 분석 기반 결정)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerBackground)
            .onSizeChanged { viewerContainerSize = it },
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
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = state.translationMode == TranslationMode.NONE,
                    ) { page ->
                        val entryName = state.imageEntries.getOrNull(page)
                        val pageModel = entryName?.let {
                            ViewerPageModel(archivePath = state.filePath, entryNames = listOf(it))
                        }
                        ZoomableImage(
                            pageModel = pageModel,
                            page = page,
                            keepZoomState = state.preference.keepZoomState,
                            isAreaSelectMode = state.translationMode == TranslationMode.AREA_SELECT,
                            cropRect = if (page == state.currentPage) state.cropRect else null,
                            ocrResults = if (page == state.currentPage) state.ocrResults else emptyList(),
                            translationResults = if (page == state.currentPage) state.translationResults else emptyList(),
                            showTranslationOverlay = state.showTranslationOverlay,
                            overlayColors = if (page == state.currentPage) state.overlayColors else emptyList(),
                            ocrImageWidth = if (page == state.currentPage) state.ocrImageWidth else 0,
                            ocrImageHeight = if (page == state.currentPage) state.ocrImageHeight else 0,
                            pagerSettledPage = pagerState.settledPage,
                            isTranslationActive = state.translationMode != TranslationMode.NONE,
                            onTapLeft = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((page - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((page + 1).coerceAtMost(pagerPageCount - 1))
                                }
                            },
                            onUpdateCropRect = { rect ->
                                viewModel.onIntent(ViewerIntent.UpdateCropRect(rect))
                            },
                            onTextBlockTap = { block ->
                                viewModel.onIntent(
                                    ViewerIntent.UpdateCropRect(
                                        CropRect(
                                            startX = block.x.toFloat(),
                                            startY = block.y.toFloat(),
                                            endX = (block.x + block.width).toFloat(),
                                            endY = (block.y + block.height).toFloat(),
                                        )
                                    )
                                )
                                viewModel.onIntent(
                                    ViewerIntent.ConfirmCrop(
                                        containerWidth = viewerContainerSize.width,
                                        containerHeight = viewerContainerSize.height,
                                    )
                                )
                            },
                        )
                    }
                }

                ViewMode.DOUBLE_PAGE -> {
                    val isRtl = state.preference.readingDirection == ReadingDirection.RTL
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = isRtl,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = state.translationMode == TranslationMode.NONE,
                    ) { spreadIndex ->
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
                            isAreaSelectMode = state.translationMode == TranslationMode.AREA_SELECT,
                            cropRect = if (isCurrentSpread) state.cropRect else null,
                            ocrResults = if (isCurrentSpread) state.ocrResults else emptyList(),
                            translationResults = if (isCurrentSpread) state.translationResults else emptyList(),
                            showTranslationOverlay = state.showTranslationOverlay,
                            overlayColors = if (isCurrentSpread) state.overlayColors else emptyList(),
                            ocrImageWidth = if (isCurrentSpread) state.ocrImageWidth else 0,
                            ocrImageHeight = if (isCurrentSpread) state.ocrImageHeight else 0,
                            pagerSettledPage = pagerState.settledPage,
                            isTranslationActive = state.translationMode != TranslationMode.NONE,
                            onTapLeft = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((spreadIndex - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((spreadIndex + 1).coerceAtMost(pagerPageCount - 1))
                                }
                            },
                            onUpdateCropRect = { rect ->
                                viewModel.onIntent(ViewerIntent.UpdateCropRect(rect))
                            },
                            onTextBlockTap = { block ->
                                viewModel.onIntent(
                                    ViewerIntent.UpdateCropRect(
                                        CropRect(
                                            startX = block.x.toFloat(),
                                            startY = block.y.toFloat(),
                                            endX = (block.x + block.width).toFloat(),
                                            endY = (block.y + block.height).toFloat(),
                                        )
                                    )
                                )
                                viewModel.onIntent(
                                    ViewerIntent.ConfirmCrop(
                                        containerWidth = viewerContainerSize.width,
                                        containerHeight = viewerContainerSize.height,
                                    )
                                )
                            },
                        )
                    }
                }

                ViewMode.SINGLE_PAGE -> {
                    // 기존 HorizontalPager 코드 그대로 유지
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = state.preference.readingDirection == ReadingDirection.RTL,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = state.translationMode == TranslationMode.NONE,
                    ) { page ->
                        val entryName = state.imageEntries.getOrNull(page)
                        val pageModel = entryName?.let {
                            ViewerPageModel(archivePath = state.filePath, entryNames = listOf(it))
                        }
                        ZoomableImage(
                            pageModel = pageModel,
                            page = page,
                            keepZoomState = state.preference.keepZoomState,
                            isAreaSelectMode = state.translationMode == TranslationMode.AREA_SELECT,
                            cropRect = if (page == state.currentPage) state.cropRect else null,
                            ocrResults = if (page == state.currentPage) state.ocrResults else emptyList(),
                            translationResults = if (page == state.currentPage) state.translationResults else emptyList(),
                            showTranslationOverlay = state.showTranslationOverlay,
                            overlayColors = if (page == state.currentPage) state.overlayColors else emptyList(),
                            ocrImageWidth = if (page == state.currentPage) state.ocrImageWidth else 0,
                            ocrImageHeight = if (page == state.currentPage) state.ocrImageHeight else 0,
                            pagerSettledPage = pagerState.settledPage,
                            isTranslationActive = state.translationMode != TranslationMode.NONE,
                            onTapLeft = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((page - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage((page + 1).coerceAtMost(pagerPageCount - 1))
                                }
                            },
                            onUpdateCropRect = { rect ->
                                viewModel.onIntent(ViewerIntent.UpdateCropRect(rect))
                            },
                            onTextBlockTap = { block ->
                                viewModel.onIntent(
                                    ViewerIntent.UpdateCropRect(
                                        CropRect(
                                            startX = block.x.toFloat(),
                                            startY = block.y.toFloat(),
                                            endX = (block.x + block.width).toFloat(),
                                            endY = (block.y + block.height).toFloat(),
                                        )
                                    )
                                )
                                viewModel.onIntent(
                                    ViewerIntent.ConfirmCrop(
                                        containerWidth = viewerContainerSize.width,
                                        containerHeight = viewerContainerSize.height,
                                    )
                                )
                            },
                        )
                    }
                }
            }
        } else {
            Text(
                text = "이미지를 찾을 수 없습니다",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // OCR 처리 중 중앙 인디케이터
        if (state.isOcrProcessing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = OcrButtonColor,
                strokeWidth = 3.dp,
            )
        }

        // 크롭 모드 전용 툴바
        AnimatedVisibility(
            visible = state.translationMode == TranslationMode.AREA_SELECT,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    viewModel.onIntent(ViewerIntent.SetTranslationMode(TranslationMode.NONE))
                    showTranslationOptions = false
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "뒤로가기",
                        tint = Color.White,
                    )
                }

                Text(
                    text = "영역 선택",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )

                val hasOcrResults = state.ocrBottomSheetText != null
                Box(
                    modifier = Modifier
                        .background(
                            color = if (hasOcrResults) Color.White.copy(alpha = 0.2f) else AreaSelectBorder,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .then(
                            if (hasOcrResults) {
                                Modifier.border(1.dp, AreaSelectBorder, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            if (hasOcrResults) {
                                // 다시 선택: OCR 초기화 + 크롭 박스 재생성
                                viewModel.onIntent(ViewerIntent.SetTranslationMode(TranslationMode.AREA_SELECT))
                            } else {
                                viewModel.onIntent(
                                    ViewerIntent.ConfirmCrop(
                                        containerWidth = viewerContainerSize.width,
                                        containerHeight = viewerContainerSize.height,
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (hasOcrResults) "다시 선택" else "선택 완료",
                        color = if (hasOcrResults) AreaSelectBorder else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        // 페이지 번역 모드 툴바
        AnimatedVisibility(
            visible = state.translationMode == TranslationMode.PAGE_TRANSLATE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    viewModel.onIntent(ViewerIntent.SetTranslationMode(TranslationMode.NONE))
                    showTranslationOptions = false
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "뒤로가기",
                        tint = Color.White,
                    )
                }

                Text(
                    text = "페이지 번역",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { viewModel.onIntent(ViewerIntent.ToggleTranslationOverlay) },
                    enabled = state.pipelineStage == PipelineStage.DONE && state.translationResults.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = if (state.showTranslationOverlay) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = "번역 오버레이 토글",
                        tint = if (state.pipelineStage == PipelineStage.DONE && state.translationResults.isNotEmpty()) Color.White
                        else Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }

        // 기본 상단 헤더 (번역 모드가 아닐 때만)
        AnimatedVisibility(
            visible = state.showControls && state.translationMode == TranslationMode.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OverlayBackground)
                    .pointerInput(Unit) { detectTapGestures { /* 터치 소비 */ } }
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 뒤로가기 아이콘
                IconButton(onClick = {
                    viewModel.onIntent(ViewerIntent.SaveProgress)
                    onBack()
                }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = "뒤로가기",
                        tint = Color.White,
                    )
                }

                // 파일명
                Text(
                    text = state.archive?.displayName() ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // 번역 버튼 - 옵션 표시 토글
                Box(
                    modifier = Modifier
                        .background(
                            color = OcrButtonColor,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                showTranslationOptions = !showTranslationOptions
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "번역",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 설정 아이콘
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "설정",
                        tint = Color.White,
                    )
                }
            }
        }

        // 번역 옵션 바 (선택영역번역 / 페이지번역)
        AnimatedVisibility(
            visible = showTranslationOptions && state.translationMode == TranslationMode.NONE,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TranslationOptionBar(
                currentMode = state.translationMode,
                onAreaSelect = {
                    viewModel.onIntent(ViewerIntent.SetTranslationMode(TranslationMode.AREA_SELECT))
                    showTranslationOptions = false
                },
                onPageTranslate = {
                    viewModel.onIntent(ViewerIntent.SetTranslationMode(TranslationMode.PAGE_TRANSLATE))
                    viewModel.onIntent(ViewerIntent.RunPageOcr)
                    showTranslationOptions = false
                },
                modifier = Modifier
                    .pointerInput(Unit) { detectTapGestures { /* 터치 소비 */ } }
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 52.dp),
            )
        }

        // 하단 컨트롤 (번역 모드에서는 숨김)
        AnimatedVisibility(
            visible = state.showControls && state.translationMode == TranslationMode.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OverlayBackground)
                    .pointerInput(Unit) { detectTapGestures { /* 터치 소비 */ } }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // 파이프라인 인디케이터
                if (state.pipelineStage != PipelineStage.IDLE) {
                    PipelineIndicator(
                        stage = state.pipelineStage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }

                // 1행: 페이지 표시 + RTL/LTR 배지
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isDoublePage && state.totalPages > 1) {
                            val spread = pageToSpread(state.currentPage)
                            val (left, right) = spreadToPages(spread, state.totalPages)
                            if (right != null) "${left + 1}-${right + 1} / ${state.totalPages}"
                            else "${left + 1} / ${state.totalPages}"
                        } else {
                            "${state.currentPage + 1} / ${state.totalPages}"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = buildString {
                                append(if (state.preference.readingDirection == ReadingDirection.RTL) "RTL \u2190" else "LTR \u2192")
                                when (viewMode) {
                                    ViewMode.WEBTOON -> append(" \u00B7 \uC6F9\uD230")
                                    ViewMode.DOUBLE_PAGE -> append(" \u00B7 \uC591\uBA74")
                                    ViewMode.SINGLE_PAGE -> {}
                                }
                            },
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                    }
                }

                // 2행: 슬라이더
                if (state.totalPages > 1) {
                    Slider(
                        value = if (isDoublePage) pageToSpread(state.currentPage).toFloat() else state.currentPage.toFloat(),
                        onValueChange = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(it.toInt())
                            }
                        },
                        valueRange = 0f..(pagerPageCount - 1).toFloat().coerceAtLeast(0f),
                        steps = (pagerPageCount - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = OcrButtonColor,
                            activeTrackColor = OcrButtonColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier.padding(vertical = 0.dp),
                    )
                }

                // 3행: 양쪽 끝 페이지 번호
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "1",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                    )
                    Text(
                        text = "${state.totalPages}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                    )
                }
            }
        }

        // OCR 결과 바텀시트 (expanded)
        AnimatedVisibility(
            visible = state.translationMode != TranslationMode.NONE && state.ocrBottomSheetText != null && state.isOcrSheetExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            OcrBottomSheet(
                ocrText = state.ocrBottomSheetText ?: "",
                translationResults = state.translationResults,
                isTranslating = state.isTranslating,
                onDismiss = { viewModel.onIntent(ViewerIntent.DismissOcrResult) },
            )
        }

        // OCR 결과 핸들 (collapsed - tap to expand)
        AnimatedVisibility(
            visible = state.translationMode != TranslationMode.NONE && state.ocrBottomSheetText != null && !state.isOcrSheetExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            OcrCollapsedHandle(
                onExpand = { viewModel.onIntent(ViewerIntent.ExpandOcrResult) },
            )
        }
    }
}

// ── 번역 옵션 바 ──────────────────────────────────────────────

@Composable
private fun TranslationOptionBar(
    currentMode: TranslationMode,
    onAreaSelect: () -> Unit,
    onPageTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TranslateBarBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 선택 영역 번역
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = if (currentMode == TranslationMode.AREA_SELECT) AreaSelectBg else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (currentMode == TranslationMode.AREA_SELECT) AreaSelectBorder else AreaSelectBorder.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable { onAreaSelect() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u2702 선택영역번역",
                color = AreaSelectText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // 페이지 번역
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = if (currentMode == TranslationMode.PAGE_TRANSLATE) PageTranslateBg else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (currentMode == TranslationMode.PAGE_TRANSLATE) PageTranslateBorder else PageTranslateBorder.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable { onPageTranslate() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\uD83D\uDCC4 페이지번역",
                color = PageTranslateText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── 파이프라인 인디케이터 ─────────────────────────────────────

@Composable
private fun PipelineIndicator(
    stage: PipelineStage,
    modifier: Modifier = Modifier,
) {
    val stages = listOf(
        "탐지" to PipelineStage.DETECTING,
        "크롭" to PipelineStage.CROPPING,
        "OCR" to PipelineStage.RECOGNIZING,
        "번역" to PipelineStage.TRANSLATING,
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEachIndexed { index, (label, pipeStage) ->
            val ordinal = pipeStage.ordinal
            val currentOrdinal = stage.ordinal

            val (symbol, color) = when {
                stage == PipelineStage.DONE || currentOrdinal > ordinal -> "\u2713" to PipelineGreen
                currentOrdinal == ordinal -> "\u25CE" to PipelineOrange
                else -> "\u25CB" to Color.White.copy(alpha = 0.3f)
            }

            Text(
                text = "$label $symbol",
                color = color,
                fontSize = 11.sp,
                fontWeight = if (currentOrdinal == ordinal) FontWeight.Bold else FontWeight.Normal,
            )

            if (index < stages.lastIndex) {
                val lineColor = if (stage == PipelineStage.DONE || currentOrdinal > ordinal + 1) {
                    PipelineGreen
                } else {
                    Color.White.copy(alpha = 0.15f)
                }
                Text(
                    text = " \u2192 ",
                    color = lineColor,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ── OCR 결과 바텀시트 ─────────────────────────────────────────

@Composable
private fun OcrBottomSheet(
    ocrText: String,
    translationResults: List<TranslationResult>,
    isTranslating: Boolean,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .pointerInput(Unit) { detectTapGestures { /* 터치 소비: 하위 zoomable로 이벤트 전파 방지 */ } }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // 드래그 핸들 (닫기 영역)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 스크롤 가능한 번역 결과 영역
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(scrollState),
        ) {
            if (translationResults.isNotEmpty()) {
                // 라인별 원문/번역 쌍 표시
                translationResults.forEachIndexed { index, result ->
                    // 원문
                    Text(
                        text = result.originalText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    // 번역
                    Text(
                        text = "\u2192 ${result.translatedText}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (index < translationResults.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                // 번역 결과 없을 때 원문만 표시
                Text(
                    text = ocrText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isTranslating) "\u2192 번역 중..." else "\u2192 번역 대기",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 엔진 배지
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "manga-ocr",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (translationResults.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF1DB954).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "papago",
                        color = Color(0xFF1DB954),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ── OCR 결과 축소 핸들 ───────────────────────────────────────────

@Composable
private fun OcrCollapsedHandle(
    onExpand: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpand() }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "번역 결과 보기",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
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
    isAreaSelectMode: Boolean,
    cropRect: CropRect?,
    ocrResults: List<TextBlock>,
    translationResults: List<TranslationResult>,
    showTranslationOverlay: Boolean,
    overlayColors: List<androidx.compose.ui.graphics.Color>,
    ocrImageWidth: Int,
    ocrImageHeight: Int,
    pagerSettledPage: Int,
    isTranslationActive: Boolean,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
    onUpdateCropRect: (CropRect) -> Unit,
    onTextBlockTap: (TextBlock) -> Unit,
) {
    val zoomableState = rememberZoomableState()

    // Reset zoom when page scrolls off-screen (unless keepZoomState is enabled)
    if (!keepZoomState && pagerSettledPage != page) {
        LaunchedEffect(Unit) {
            zoomableState.resetZoom(animationSpec = snap())
        }
    }

    var containerWidth by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidth = it.width }
            .zoomable(
                state = zoomableState,
                enabled = !isAreaSelectMode,
                onClick = if (!isAreaSelectMode && !isTranslationActive) {
                    { offset ->
                        when {
                            offset.x < containerWidth * 0.2f -> onTapLeft()
                            offset.x > containerWidth * 0.8f -> onTapRight()
                            else -> onTapCenter()
                        }
                    }
                } else {
                    null
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (pageModel != null) {
            val painter = rememberAsyncImagePainter(
                model = pageModel,
                contentScale = ContentScale.Fit,
            )

            Image(
                painter = painter,
                contentDescription = "Page ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            val painterState = painter.state.collectAsState().value
            when (painterState) {
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(color = Color.White)
                }
                is AsyncImagePainter.State.Success -> {
                    val imgWidth = painterState.result.image.width
                    val imgHeight = painterState.result.image.height

                    // OCR 원본 이미지 크기 사용 (Coil 다운샘플링 대응)
                    val overlayWidth = if (ocrImageWidth > 0) ocrImageWidth else imgWidth
                    val overlayHeight = if (ocrImageHeight > 0) ocrImageHeight else imgHeight

                    // Bounding Box 하이라이트 (오버레이 표시 중에는 숨김)
                    if (ocrResults.isNotEmpty() && !showTranslationOverlay) {
                        BoundingBoxOverlay(
                            textBlocks = ocrResults,
                            imageWidth = overlayWidth,
                            imageHeight = overlayHeight,
                            onBlockTap = onTextBlockTap,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 번역 오버레이
                    if (showTranslationOverlay && translationResults.isNotEmpty() && ocrResults.isNotEmpty()) {
                        TranslationOverlay(
                            textBlocks = ocrResults,
                            translationResults = translationResults,
                            dominantColors = overlayColors,
                            imageWidth = overlayWidth,
                            imageHeight = overlayHeight,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 선택 영역 크롭 오버레이
                    if (isAreaSelectMode) {
                        CropOverlay(
                            cropRect = cropRect,
                            onUpdateCropRect = onUpdateCropRect,
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

// ── Bounding Box 오버레이 ─────────────────────────────────────

@Composable
private fun BoundingBoxOverlay(
    textBlocks: List<TextBlock>,
    imageWidth: Int,
    imageHeight: Int,
    onBlockTap: (TextBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)

    Canvas(
        modifier = modifier
            .pointerInput(textBlocks, imageWidth, imageHeight) {
                detectTapGestures { offset ->
                    // ContentScale.Fit 변환 계산
                    val fitScale = minOf(
                        size.width.toFloat() / imageWidth,
                        size.height.toFloat() / imageHeight,
                    )
                    val offsetX = (size.width - imageWidth * fitScale) / 2f
                    val offsetY = (size.height - imageHeight * fitScale) / 2f

                    textBlocks.firstOrNull { block ->
                        val lx = block.x * fitScale + offsetX
                        val ly = block.y * fitScale + offsetY
                        val lw = block.width * fitScale
                        val lh = block.height * fitScale
                        offset.x in lx..(lx + lw) && offset.y in ly..(ly + lh)
                    }?.let { onBlockTap(it) }
                }
            },
    ) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas
        // ContentScale.Fit 변환: 이미지 픽셀 좌표 → 레이아웃 좌표
        val fitScale = minOf(
            size.width / imageWidth.toFloat(),
            size.height / imageHeight.toFloat(),
        )
        val offsetX = (size.width - imageWidth * fitScale) / 2f
        val offsetY = (size.height - imageHeight * fitScale) / 2f

        textBlocks.forEach { block ->
            drawRect(
                color = BoundingBoxColor,
                topLeft = Offset(
                    x = block.x * fitScale + offsetX,
                    y = block.y * fitScale + offsetY,
                ),
                size = Size(
                    width = block.width * fitScale,
                    height = block.height * fitScale,
                ),
                style = Stroke(
                    width = 2f,
                    pathEffect = dashEffect,
                ),
            )
        }
    }
}

// ── 크롭 오버레이 ─────────────────────────────────────────────

@Composable
private fun CropOverlay(
    cropRect: CropRect?,
    onUpdateCropRect: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val handleRadius = 18f
    val handleTouchRadius = 64f // 터치 영역은 시각적 핸들보다 크게/
    val minCropSize = 60f

    // Always measure container - must happen before early return
    Box(modifier = modifier.onSizeChanged { containerSize = it }) {
        // Initialize default centered 1:1 crop box when sentinel values detected
        LaunchedEffect(cropRect, containerSize) {
            if (cropRect != null && cropRect.startX < 0f && containerSize.width > 0) {
                val boxSize = minOf(containerSize.width, containerSize.height) * 0.5f
                val cx = containerSize.width / 2f
                val cy = containerSize.height / 2f
                onUpdateCropRect(
                    CropRect(
                        startX = cx - boxSize / 2f,
                        startY = cy - boxSize / 2f,
                        endX = cx + boxSize / 2f,
                        endY = cy + boxSize / 2f,
                    )
                )
            }
        }

        if (cropRect == null || cropRect.startX < 0f) return@Box

        val minX = minOf(cropRect.startX, cropRect.endX)
        val minY = minOf(cropRect.startY, cropRect.endY)
        val maxX = maxOf(cropRect.startX, cropRect.endX)
        val maxY = maxOf(cropRect.startY, cropRect.endY)
        val rectWidth = maxX - minX
        val rectHeight = maxY - minY

        var dragMode by remember { mutableStateOf(DragMode.NONE) }
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
        // rememberUpdatedState로 최신 cropRect를 pointerInput 내부에서 참조
        // pointerInput(Unit)을 사용해 드래그 중 재시작 방지
        val currentCropRect by rememberUpdatedState(cropRect)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cr = currentCropRect ?: return@detectDragGestures
                            val cMinX = minOf(cr.startX, cr.endX)
                            val cMinY = minOf(cr.startY, cr.endY)
                            val cMaxX = maxOf(cr.startX, cr.endX)
                            val cMaxY = maxOf(cr.startY, cr.endY)
                            val corners = listOf(
                                DragMode.TOP_LEFT to Offset(cMinX, cMinY),
                                DragMode.TOP_RIGHT to Offset(cMaxX, cMinY),
                                DragMode.BOTTOM_LEFT to Offset(cMinX, cMaxY),
                                DragMode.BOTTOM_RIGHT to Offset(cMaxX, cMaxY),
                            )
                            val hitCorner = corners.firstOrNull { (_, pos) ->
                                (offset - pos).getDistance() < handleTouchRadius
                            }
                            dragMode = if (hitCorner != null) {
                                hitCorner.first
                            } else {
                                DragMode.MOVE
                            }
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val cr = currentCropRect ?: return@detectDragGestures
                            val cMinX = minOf(cr.startX, cr.endX)
                            val cMinY = minOf(cr.startY, cr.endY)
                            val cMaxX = maxOf(cr.startX, cr.endX)
                            val cMaxY = maxOf(cr.startY, cr.endY)
                            val cWidth = cMaxX - cMinX
                            val cHeight = cMaxY - cMinY
                            val w = containerSize.width.toFloat()
                            val h = containerSize.height.toFloat()
                            when (dragMode) {
                                DragMode.MOVE -> {
                                    val newMinX = (cMinX + delta.x).coerceIn(0f, w - cWidth)
                                    val newMinY = (cMinY + delta.y).coerceIn(0f, h - cHeight)
                                    onUpdateCropRect(
                                        CropRect(
                                            startX = newMinX,
                                            startY = newMinY,
                                            endX = newMinX + cWidth,
                                            endY = newMinY + cHeight,
                                        )
                                    )
                                }
                                DragMode.TOP_LEFT -> {
                                    val newX = (cMinX + delta.x).coerceIn(0f, cMaxX - minCropSize)
                                    val newY = (cMinY + delta.y).coerceIn(0f, cMaxY - minCropSize)
                                    onUpdateCropRect(CropRect(startX = newX, startY = newY, endX = cMaxX, endY = cMaxY))
                                }
                                DragMode.TOP_RIGHT -> {
                                    val newX = (cMaxX + delta.x).coerceIn(cMinX + minCropSize, w)
                                    val newY = (cMinY + delta.y).coerceIn(0f, cMaxY - minCropSize)
                                    onUpdateCropRect(CropRect(startX = cMinX, startY = newY, endX = newX, endY = cMaxY))
                                }
                                DragMode.BOTTOM_LEFT -> {
                                    val newX = (cMinX + delta.x).coerceIn(0f, cMaxX - minCropSize)
                                    val newY = (cMaxY + delta.y).coerceIn(cMinY + minCropSize, h)
                                    onUpdateCropRect(CropRect(startX = newX, startY = cMinY, endX = cMaxX, endY = newY))
                                }
                                DragMode.BOTTOM_RIGHT -> {
                                    val newX = (cMaxX + delta.x).coerceIn(cMinX + minCropSize, w)
                                    val newY = (cMaxY + delta.y).coerceIn(cMinY + minCropSize, h)
                                    onUpdateCropRect(CropRect(startX = cMinX, startY = cMinY, endX = newX, endY = newY))
                                }
                                DragMode.NONE -> {}
                            }
                        },
                        onDragEnd = { dragMode = DragMode.NONE },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dimColor = Color.Black.copy(alpha = 0.5f)
                drawRect(dimColor, Offset.Zero, Size(size.width, minY))
                drawRect(dimColor, Offset(0f, maxY), Size(size.width, size.height - maxY))
                drawRect(dimColor, Offset(0f, minY), Size(minX, rectHeight))
                drawRect(dimColor, Offset(maxX, minY), Size(size.width - maxX, rectHeight))

                drawRect(
                    color = AreaSelectBorder,
                    topLeft = Offset(minX, minY),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(width = 2f, pathEffect = dashEffect),
                )

                val corners = listOf(
                    Offset(minX, minY),
                    Offset(maxX, minY),
                    Offset(minX, maxY),
                    Offset(maxX, maxY),
                )
                corners.forEach { corner ->
                    drawCircle(color = Color.White, radius = handleRadius, center = corner)
                    drawCircle(color = AreaSelectBorder, radius = handleRadius - 3f, center = corner)
                }

                val edges = listOf(
                    Offset((minX + maxX) / 2f, minY),
                    Offset((minX + maxX) / 2f, maxY),
                    Offset(minX, (minY + maxY) / 2f),
                    Offset(maxX, (minY + maxY) / 2f),
                )
                edges.forEach { edge ->
                    drawCircle(color = Color.White, radius = 6f, center = edge)
                    drawCircle(color = AreaSelectBorder, radius = 4f, center = edge)
                }
            }
        }
    }
}

private enum class DragMode {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}
