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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.key
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
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import me.saket.telephoto.subsamplingimage.SubSamplingImage
import me.saket.telephoto.subsamplingimage.rememberSubSamplingImageState
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
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

    val pagerState = key(viewMode) {
        rememberPagerState(
            initialPage = if (isDoublePage) pageToSpread(state.currentPage) else state.currentPage,
            pageCount = { pagerPageCount },
        )
    }

    val coroutineScope = rememberCoroutineScope()

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

    var viewerContainerSize by remember { mutableStateOf(IntSize.Zero) }
    // 현재 줌 변환 정보 (CropOverlay → ConfirmCrop 좌표 변환용)
    var zoomScaleX by remember { mutableStateOf(1f) }
    var zoomScaleY by remember { mutableStateOf(1f) }
    var zoomOffsetX by remember { mutableStateOf(0f) }
    var zoomOffsetY by remember { mutableStateOf(0f) }
    var zoomOriginX by remember { mutableStateOf(0.5f) }
    var zoomOriginY by remember { mutableStateOf(0.5f) }
    var showViewModeDialog by remember { mutableStateOf(false) }

    if (showViewModeDialog) {
        ViewModeSettingsDialog(
            preference = state.preference,
            onSetViewMode = { viewModel.onIntent(ViewerIntent.SetViewMode(it)) },
            onSetReadingDirection = { viewModel.onIntent(ViewerIntent.SetReadingDirection(it)) },
            onDismiss = { showViewModeDialog = false },
        )
    }

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
                            imageScaleMode = state.preference.imageScaleMode,
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
                                    pagerState.scrollToPage((page - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage((page + 1).coerceAtMost(pagerPageCount - 1))
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
                                        zoomScaleX = zoomScaleX,
                                        zoomScaleY = zoomScaleY,
                                        zoomOffsetX = zoomOffsetX,
                                        zoomOffsetY = zoomOffsetY,
                                        transformOriginX = zoomOriginX,
                                        transformOriginY = zoomOriginY,
                                    )
                                )
                            },
                            cachedBlocks = if (page == state.currentPage) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (page == state.currentPage) state.cachedImageWidth else 0,
                            cachedImageHeight = if (page == state.currentPage) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (page == state.currentPage) state.cachedOverlayColors else emptyList(),
                            visibleCachedBlockIndices = if (page == state.currentPage) state.visibleCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockOverlay(it)) },
                            onZoomTransformChanged = { sx, sy, ox, oy, origX, origY ->
                                zoomScaleX = sx; zoomScaleY = sy
                                zoomOffsetX = ox; zoomOffsetY = oy
                                zoomOriginX = origX; zoomOriginY = origY
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
                            imageScaleMode = state.preference.imageScaleMode,
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
                                    pagerState.scrollToPage((spreadIndex - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage((spreadIndex + 1).coerceAtMost(pagerPageCount - 1))
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
                                        zoomScaleX = zoomScaleX,
                                        zoomScaleY = zoomScaleY,
                                        zoomOffsetX = zoomOffsetX,
                                        zoomOffsetY = zoomOffsetY,
                                        transformOriginX = zoomOriginX,
                                        transformOriginY = zoomOriginY,
                                    )
                                )
                            },
                            cachedBlocks = if (isCurrentSpread) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (isCurrentSpread) state.cachedImageWidth else 0,
                            cachedImageHeight = if (isCurrentSpread) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (isCurrentSpread) state.cachedOverlayColors else emptyList(),
                            visibleCachedBlockIndices = if (isCurrentSpread) state.visibleCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockOverlay(it)) },
                            onZoomTransformChanged = { sx, sy, ox, oy, origX, origY ->
                                zoomScaleX = sx; zoomScaleY = sy
                                zoomOffsetX = ox; zoomOffsetY = oy
                                zoomOriginX = origX; zoomOriginY = origY
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
                            imageScaleMode = state.preference.imageScaleMode,
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
                                    pagerState.scrollToPage((page - 1).coerceAtLeast(0))
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage((page + 1).coerceAtMost(pagerPageCount - 1))
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
                                        zoomScaleX = zoomScaleX,
                                        zoomScaleY = zoomScaleY,
                                        zoomOffsetX = zoomOffsetX,
                                        zoomOffsetY = zoomOffsetY,
                                        transformOriginX = zoomOriginX,
                                        transformOriginY = zoomOriginY,
                                    )
                                )
                            },
                            cachedBlocks = if (page == state.currentPage) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (page == state.currentPage) state.cachedImageWidth else 0,
                            cachedImageHeight = if (page == state.currentPage) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (page == state.currentPage) state.cachedOverlayColors else emptyList(),
                            visibleCachedBlockIndices = if (page == state.currentPage) state.visibleCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockOverlay(it)) },
                            onZoomTransformChanged = { sx, sy, ox, oy, origX, origY ->
                                zoomScaleX = sx; zoomScaleY = sy
                                zoomOffsetX = ox; zoomOffsetY = oy
                                zoomOriginX = origX; zoomOriginY = origY
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
                                        zoomScaleX = zoomScaleX,
                                        zoomScaleY = zoomScaleY,
                                        zoomOffsetX = zoomOffsetX,
                                        zoomOffsetY = zoomOffsetY,
                                        transformOriginX = zoomOriginX,
                                        transformOriginY = zoomOriginY,
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
                            .clickable { showViewModeDialog = true }
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

                // 이미지 비율 선택
                ImageScaleModeSelector(
                    currentMode = state.preference.imageScaleMode,
                    onModeSelected = { mode ->
                        viewModel.onIntent(ViewerIntent.SetImageScaleMode(mode))
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )

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
    imageScaleMode: ImageScaleMode,
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
    cachedBlocks: List<TranslationBlock>,
    cachedImageWidth: Int,
    cachedImageHeight: Int,
    cachedOverlayColors: List<androidx.compose.ui.graphics.Color>,
    visibleCachedBlockIndices: Set<Int>,
    onCachedBlockTap: (Int) -> Unit,
    onZoomTransformChanged: (scaleX: Float, scaleY: Float, offsetX: Float, offsetY: Float, originX: Float, originY: Float) -> Unit,
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

    // 줌 변환 정보를 상위 컴포저블로 리포트
    val transform = zoomableState.contentTransformation
    LaunchedEffect(transform) {
        if (transform.isSpecified) {
            onZoomTransformChanged(
                transform.scale.scaleX,
                transform.scale.scaleY,
                transform.offset.x,
                transform.offset.y,
                transform.transformOrigin.pivotFractionX,
                transform.transformOrigin.pivotFractionY,
            )
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // onClick 람다가 telephoto 내부에서 캐싱되므로, 최신 값을 항상 참조하도록 rememberUpdatedState 사용
    val currentCachedBlocks by rememberUpdatedState(cachedBlocks)
    val currentCachedImageWidth by rememberUpdatedState(cachedImageWidth)
    val currentCachedImageHeight by rememberUpdatedState(cachedImageHeight)
    val currentOnCachedBlockTap by rememberUpdatedState(onCachedBlockTap)

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
                        gestures = if (!isAreaSelectMode) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
                        onClick = if (!isAreaSelectMode && !isTranslationActive) {
                            { offset ->
                                var handled = false
                                if (currentCachedBlocks.isNotEmpty() && currentCachedImageWidth > 0 && currentCachedImageHeight > 0) {
                                    // SubSamplingImage 경로: contentTransformation이 ContentScale을 포함하므로
                                    // 역변환만으로 이미지 픽셀 좌표를 얻을 수 있다 (calculateImageTransform 불필요)
                                    val tf = zoomableState.contentTransformation
                                    val originPxX = tf.transformOrigin.pivotFractionX * containerSize.width
                                    val originPxY = tf.transformOrigin.pivotFractionY * containerSize.height
                                    val imgX = (offset.x - tf.offset.x - originPxX) / tf.scale.scaleX + originPxX
                                    val imgY = (offset.y - tf.offset.y - originPxY) / tf.scale.scaleY + originPxY
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
                            }
                        } else {
                            null
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
                        contentDescription = "Page ${page + 1}",
                        modifier = Modifier.fillMaxSize(),
                        // baseZoom 적용 전까지 투명하게 유지하여 깜빡임 방지
                        alpha = if (subSamplingReady || baseZoomFactor <= 1f) 1f else 0f,
                    )

                    // OCR 원본 이미지 크기 사용
                    val overlayWidth = if (ocrImageWidth > 0) ocrImageWidth else result.imageSize.width
                    val overlayHeight = if (ocrImageHeight > 0) ocrImageHeight else result.imageSize.height

                    // Bounding Box 하이라이트 (오버레이 표시 중에는 숨김)
                    if (ocrResults.isNotEmpty() && !showTranslationOverlay) {
                        BoundingBoxOverlay(
                            textBlocks = ocrResults,
                            imageWidth = overlayWidth,
                            imageHeight = overlayHeight,
                            imageScaleMode = ImageScaleMode.AUTO_FIT,
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
                            imageScaleMode = ImageScaleMode.AUTO_FIT,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // 캐시된 말풍선 개별 오버레이 (일반 모드)
                    if (cachedBlocks.isNotEmpty() && visibleCachedBlockIndices.isNotEmpty()) {
                        val (visibleTextBlocks, visibleTranslations, visibleColors) = remember(
                            visibleCachedBlockIndices, cachedBlocks, cachedOverlayColors,
                        ) {
                            val sorted = visibleCachedBlockIndices.sorted()
                            Triple(
                                sorted.map { i -> cachedBlocks[i].let { TextBlock(it.x, it.y, it.width, it.height, it.originalText) } },
                                sorted.map { i -> cachedBlocks[i].let { TranslationResult(it.originalText, it.translatedText) } },
                                sorted.map { i -> cachedOverlayColors.getOrElse(i) { androidx.compose.ui.graphics.Color.White } },
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
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // 선택 영역 크롭 오버레이 (zoomable 외부 — 화면 좌표계)
            if (isAreaSelectMode) {
                CropOverlay(
                    cropRect = cropRect,
                    onUpdateCropRect = onUpdateCropRect,
                    modifier = Modifier.fillMaxSize(),
                )
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
                        gestures = if (!isAreaSelectMode) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
                        onClick = if (!isAreaSelectMode && !isTranslationActive) {
                            { offset ->
                                var handled = false
                                if (currentCachedBlocks.isNotEmpty() && currentCachedImageWidth > 0 && currentCachedImageHeight > 0) {
                                    // Coil 경로: setContentLocation으로 실제 이미지 크기를 전달하므로
                                    // contentTransformation이 ContentScale을 포함한다 (calculateImageTransform 불필요)
                                    val tf = zoomableState.contentTransformation
                                    val originPxX = tf.transformOrigin.pivotFractionX * containerSize.width
                                    val originPxY = tf.transformOrigin.pivotFractionY * containerSize.height
                                    val imgX = (offset.x - tf.offset.x - originPxX) / tf.scale.scaleX + originPxX
                                    val imgY = (offset.y - tf.offset.y - originPxY) / tf.scale.scaleY + originPxY
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
                            }
                        } else {
                            null
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
                        contentDescription = "Page ${page + 1}",
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

                            // OCR 원본 이미지 크기 사용 (Coil 다운샘플링 대응)
                            val overlayWidth = if (ocrImageWidth > 0) ocrImageWidth else imgWidth
                            val overlayHeight = if (ocrImageHeight > 0) ocrImageHeight else imgHeight

                            // Bounding Box 하이라이트 (오버레이 표시 중에는 숨김)
                            // 오버레이는 Fit 좌표계 기준으로 그림 (줌은 zoomableState가 처리)
                            if (ocrResults.isNotEmpty() && !showTranslationOverlay) {
                                BoundingBoxOverlay(
                                    textBlocks = ocrResults,
                                    imageWidth = overlayWidth,
                                    imageHeight = overlayHeight,
                                    imageScaleMode = ImageScaleMode.AUTO_FIT,
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
                                    imageScaleMode = ImageScaleMode.AUTO_FIT,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            // 캐시된 말풍선 개별 오버레이 (일반 모드)
                            if (cachedBlocks.isNotEmpty() && visibleCachedBlockIndices.isNotEmpty()) {
                                val (visibleTextBlocks, visibleTranslations, visibleColors) = remember(
                                    visibleCachedBlockIndices, cachedBlocks, cachedOverlayColors,
                                ) {
                                    val sorted = visibleCachedBlockIndices.sorted()
                                    Triple(
                                        sorted.map { i -> cachedBlocks[i].let { TextBlock(it.x, it.y, it.width, it.height, it.originalText) } },
                                        sorted.map { i -> cachedBlocks[i].let { TranslationResult(it.originalText, it.translatedText) } },
                                        sorted.map { i -> cachedOverlayColors.getOrElse(i) { androidx.compose.ui.graphics.Color.White } },
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
                            }
                        }
                        else -> {}
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // 선택 영역 크롭 오버레이 (zoomable 외부 — 화면 좌표계)
            if (isAreaSelectMode) {
                CropOverlay(
                    cropRect = cropRect,
                    onUpdateCropRect = onUpdateCropRect,
                    modifier = Modifier.fillMaxSize(),
                )
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

private fun ImageScaleMode.displayLabel(): String = when (this) {
    ImageScaleMode.FIT_WIDTH -> "가로"
    ImageScaleMode.FIT_HEIGHT -> "세로"
    ImageScaleMode.AUTO_FIT -> "자동"
    ImageScaleMode.ORIGINAL -> "원본"
}

@Composable
private fun ImageScaleModeSelector(
    currentMode: ImageScaleMode,
    onModeSelected: (ImageScaleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ImageScaleMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) OcrButtonColor.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.displayLabel(),
                    color = if (isSelected) OcrButtonColor else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ViewModeSettingsDialog(
    preference: ViewerPreference,
    onSetViewMode: (ViewMode) -> Unit,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("보기 설정") },
        text = {
            Column {
                Text(
                    text = "읽기 방향",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ReadingDirection.entries.forEach { direction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetReadingDirection(direction) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preference.readingDirection == direction,
                            onClick = { onSetReadingDirection(direction) },
                        )
                        Text(
                            text = when (direction) {
                                ReadingDirection.LTR -> "왼쪽 → 오른쪽 (LTR)"
                                ReadingDirection.RTL -> "오른쪽 → 왼쪽 (RTL)"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "보기 모드",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ViewMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetViewMode(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preference.viewMode == mode,
                            onClick = { onSetViewMode(mode) },
                        )
                        Text(
                            text = when (mode) {
                                ViewMode.SINGLE_PAGE -> "단면"
                                ViewMode.DOUBLE_PAGE -> "양면"
                                ViewMode.WEBTOON -> "웹툰 (세로 스크롤)"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}
