package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.HorizontalDivider
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
import com.manhwaviewer.domain.entity.OcrBlock
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.auto_process_none
import manhwa_viewer.presentation.generated.resources.auto_process_ocr
import manhwa_viewer.presentation.generated.resources.auto_process_translate
import manhwa_viewer.presentation.generated.resources.back
import manhwa_viewer.presentation.generated.resources.close
import manhwa_viewer.presentation.generated.resources.dialog_auto_process_title
import manhwa_viewer.presentation.generated.resources.dialog_reading_direction
import manhwa_viewer.presentation.generated.resources.dialog_view_mode
import manhwa_viewer.presentation.generated.resources.dialog_view_settings_title
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import manhwa_viewer.presentation.generated.resources.ic_book
import manhwa_viewer.presentation.generated.resources.ic_translate
import manhwa_viewer.presentation.generated.resources.pipeline_cropping
import manhwa_viewer.presentation.generated.resources.pipeline_detecting
import manhwa_viewer.presentation.generated.resources.pipeline_recognizing
import manhwa_viewer.presentation.generated.resources.pipeline_translating
import manhwa_viewer.presentation.generated.resources.reading_direction_ltr
import manhwa_viewer.presentation.generated.resources.reading_direction_rtl
import manhwa_viewer.presentation.generated.resources.scale_auto_fit
import manhwa_viewer.presentation.generated.resources.scale_fit_height
import manhwa_viewer.presentation.generated.resources.scale_fit_width
import manhwa_viewer.presentation.generated.resources.scale_original
import manhwa_viewer.presentation.generated.resources.status_ocr_done
import manhwa_viewer.presentation.generated.resources.status_ocr_failed
import manhwa_viewer.presentation.generated.resources.status_ocr_processing
import manhwa_viewer.presentation.generated.resources.status_translate_done
import manhwa_viewer.presentation.generated.resources.status_translate_failed
import manhwa_viewer.presentation.generated.resources.status_translate_processing
import manhwa_viewer.presentation.generated.resources.view_mode_double
import manhwa_viewer.presentation.generated.resources.view_mode_single
import manhwa_viewer.presentation.generated.resources.view_mode_webtoon
import manhwa_viewer.presentation.generated.resources.view_mode_webtoon_full
import manhwa_viewer.presentation.generated.resources.viewer_area_select
import manhwa_viewer.presentation.generated.resources.viewer_area_translate
import manhwa_viewer.presentation.generated.resources.viewer_image_not_found
import manhwa_viewer.presentation.generated.resources.viewer_page_translate
import manhwa_viewer.presentation.generated.resources.viewer_page_translate_title
import manhwa_viewer.presentation.generated.resources.viewer_reselect
import manhwa_viewer.presentation.generated.resources.viewer_select_done
import manhwa_viewer.presentation.generated.resources.viewer_show_translation
import manhwa_viewer.presentation.generated.resources.viewer_toggle_overlay
import manhwa_viewer.presentation.generated.resources.viewer_settings
import manhwa_viewer.presentation.generated.resources.viewer_translate
import manhwa_viewer.presentation.generated.resources.viewer_translate_selected
import manhwa_viewer.presentation.generated.resources.viewer_translating
import manhwa_viewer.presentation.generated.resources.viewer_translation_pending
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
    var showAutoProcessDialog by remember { mutableStateOf(false) }

    if (showViewModeDialog) {
        ViewModeSettingsDialog(
            preference = state.preference,
            onSetViewMode = { viewModel.onIntent(ViewerIntent.SetViewMode(it)) },
            onSetReadingDirection = { viewModel.onIntent(ViewerIntent.SetReadingDirection(it)) },
            onDismiss = { showViewModeDialog = false },
        )
    }

    if (showAutoProcessDialog) {
        AutoProcessModeDialog(
            currentMode = state.autoProcessMode,
            onModeSelected = { viewModel.onIntent(ViewerIntent.SetAutoProcessMode(it)) },
            onDismiss = { showAutoProcessDialog = false },
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
                            globalOverlayEnabled = state.globalOverlayEnabled,
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
                                        useSubSampling = true,
                                    )
                                )
                            },
                            cachedBlocks = if (page == state.currentPage) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (page == state.currentPage) state.cachedImageWidth else 0,
                            cachedImageHeight = if (page == state.currentPage) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (page == state.currentPage) state.cachedOverlayColors else emptyList(),
                            visibleCachedBlockIndices = if (page == state.currentPage) state.visibleCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockOverlay(it)) },
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
                            globalOverlayEnabled = state.globalOverlayEnabled,
                            overlayColors = if (isCurrentSpread) state.overlayColors else emptyList(),
                            ocrImageWidth = if (isCurrentSpread) state.ocrImageWidth else 0,
                            ocrImageHeight = if (isCurrentSpread) state.ocrImageHeight else 0,
                            pagerSettledPage = pagerState.settledPage,
                            isTranslationActive = state.translationMode != TranslationMode.NONE,
                            onTapLeft = {
                                coroutineScope.launch {
                                    val target = if (isRtl) (spreadIndex + 1).coerceAtMost(pagerPageCount - 1)
                                                 else (spreadIndex - 1).coerceAtLeast(0)
                                    pagerState.scrollToPage(target)
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    val target = if (isRtl) (spreadIndex - 1).coerceAtLeast(0)
                                                 else (spreadIndex + 1).coerceAtMost(pagerPageCount - 1)
                                    pagerState.scrollToPage(target)
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
                                        useSubSampling = pageModel?.entryNames?.size == 1,
                                    )
                                )
                            },
                            cachedBlocks = if (isCurrentSpread) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (isCurrentSpread) state.cachedImageWidth else 0,
                            cachedImageHeight = if (isCurrentSpread) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (isCurrentSpread) state.cachedOverlayColors else emptyList(),
                            visibleCachedBlockIndices = if (isCurrentSpread) state.visibleCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockOverlay(it)) },
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
                            onZoomTransformChanged = { sx, sy, ox, oy, origX, origY ->
                                zoomScaleX = sx; zoomScaleY = sy
                                zoomOffsetX = ox; zoomOffsetY = oy
                                zoomOriginX = origX; zoomOriginY = origY
                            },
                        )
                    }
                }

                ViewMode.SINGLE_PAGE -> {
                    val isSinglePageRtl = state.preference.readingDirection == ReadingDirection.RTL
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = isSinglePageRtl,
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
                            globalOverlayEnabled = state.globalOverlayEnabled,
                            overlayColors = if (page == state.currentPage) state.overlayColors else emptyList(),
                            ocrImageWidth = if (page == state.currentPage) state.ocrImageWidth else 0,
                            ocrImageHeight = if (page == state.currentPage) state.ocrImageHeight else 0,
                            pagerSettledPage = pagerState.settledPage,
                            isTranslationActive = state.translationMode != TranslationMode.NONE,
                            onTapLeft = {
                                coroutineScope.launch {
                                    val target = if (isSinglePageRtl) (page + 1).coerceAtMost(pagerPageCount - 1)
                                                 else (page - 1).coerceAtLeast(0)
                                    pagerState.scrollToPage(target)
                                }
                            },
                            onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                            onTapRight = {
                                coroutineScope.launch {
                                    val target = if (isSinglePageRtl) (page - 1).coerceAtLeast(0)
                                                 else (page + 1).coerceAtMost(pagerPageCount - 1)
                                    pagerState.scrollToPage(target)
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
                                        useSubSampling = true,
                                    )
                                )
                            },
                            cachedBlocks = if (page == state.currentPage) state.cachedBlocks else emptyList(),
                            cachedImageWidth = if (page == state.currentPage) state.cachedImageWidth else 0,
                            cachedImageHeight = if (page == state.currentPage) state.cachedImageHeight else 0,
                            cachedOverlayColors = if (page == state.currentPage) state.cachedOverlayColors else emptyList(),
                            visibleCachedBlockIndices = if (page == state.currentPage) state.visibleCachedBlockIndices else emptySet(),
                            onCachedBlockTap = { viewModel.onIntent(ViewerIntent.ToggleCachedBlockOverlay(it)) },
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
                text = stringResource(Res.string.viewer_image_not_found),
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
                        contentDescription = stringResource(Res.string.back),
                        tint = Color.White,
                    )
                }

                Text(
                    text = stringResource(Res.string.viewer_area_select),
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
                                val isSubSampling = viewMode != ViewMode.DOUBLE_PAGE || run {
                                    val spread = pageToSpread(state.currentPage)
                                    val (_, secondPage) = spreadToPages(spread, state.totalPages)
                                    secondPage == null
                                }
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
                                        useSubSampling = isSubSampling,
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (hasOcrResults) stringResource(Res.string.viewer_reselect) else stringResource(Res.string.viewer_select_done),
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
                        contentDescription = stringResource(Res.string.back),
                        tint = Color.White,
                    )
                }

                Text(
                    text = stringResource(Res.string.viewer_page_translate_title),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { viewModel.onIntent(ViewerIntent.ToggleGlobalOverlay) },
                    enabled = state.pipelineStage == PipelineStage.DONE && state.translationResults.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = if (state.globalOverlayEnabled) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(Res.string.viewer_toggle_overlay),
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
                        contentDescription = stringResource(Res.string.back),
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

                // 선택 번역 버튼 (바운딩 박스 선택 시에만 표시)
                AnimatedVisibility(visible = state.selectedOcrBlockIndices.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = OcrButtonColor,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    viewModel.onIntent(ViewerIntent.TranslateSelectedBlocks)
                                })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.viewer_translate_selected),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // 오버레이 아이콘
                IconButton(
                    onClick = { viewModel.onIntent(ViewerIntent.ToggleGlobalOverlay) },
                    enabled = state.isOverlayButtonEnabled,
                ) {
                    Icon(
                        imageVector = if (state.globalOverlayEnabled) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(Res.string.viewer_toggle_overlay),
                        tint = if (state.isOverlayButtonEnabled) Color.White
                        else Color.White.copy(alpha = 0.3f),
                    )
                }

                // [미사용] 번역 버튼 — 상단 툴바에서 제거됨. 기능은 TranslationOptionBar에 보존.
                // Box(
                //     modifier = Modifier
                //         .background(color = OcrButtonColor, shape = RoundedCornerShape(8.dp))
                //         .padding(horizontal = 12.dp, vertical = 4.dp)
                //         .pointerInput(Unit) {
                //             detectTapGestures(onTap = { showTranslationOptions = !showTranslationOptions })
                //         },
                //     contentAlignment = Alignment.Center,
                // ) {
                //     Text(text = stringResource(Res.string.viewer_translate), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                // }
                // Spacer(modifier = Modifier.width(4.dp))

                // [미사용] 설정 아이콘 — 상단 툴바에서 제거됨. onSettings 콜백은 보존.
                // IconButton(onClick = onSettings) {
                //     Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(Res.string.viewer_settings), tint = Color.White)
                // }
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

        // 하단 컨트롤에서 사용할 string resource 변수
        val viewModeLabel = when (viewMode) {
            ViewMode.DOUBLE_PAGE -> stringResource(Res.string.view_mode_double)
            ViewMode.WEBTOON -> stringResource(Res.string.view_mode_webtoon)
            ViewMode.SINGLE_PAGE -> stringResource(Res.string.view_mode_single)
        }
        val autoProcessModeLabel = when (state.autoProcessMode) {
            AutoProcessMode.NONE -> stringResource(Res.string.auto_process_none)
            AutoProcessMode.AUTO_OCR -> stringResource(Res.string.auto_process_ocr)
            AutoProcessMode.AUTO_TRANSLATE -> stringResource(Res.string.auto_process_translate)
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
                // Row 1: [RTL/양면 버튼] [OCR/번역 버튼] [상태 표시]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // RTL/양면 버튼
                    Box(
                        modifier = Modifier
                            .clickable { showViewModeDialog = true }
                            .background(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_book),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = buildString {
                                    append(if (state.preference.readingDirection == ReadingDirection.RTL) "RTL" else "LTR")
                                    append(" / ")
                                    append(viewModeLabel)
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // OCR/번역 버튼
                    Box(
                        modifier = Modifier
                            .clickable { showAutoProcessDialog = true }
                            .background(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_translate),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = autoProcessModeLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // 상태 표시
                    AutoProcessStatusIndicator(
                        mode = state.autoProcessMode,
                        status = state.autoProcessStatus,
                        hasCachedTranslation = state.cachedBlocks.isNotEmpty(),
                        hasCachedOcr = state.autoOcrBlocks.isNotEmpty(),
                        onRetry = { viewModel.onIntent(ViewerIntent.RetryAutoProcess) },
                    )
                }

                // Row 2: 이미지 비율 선택 (pill group)
                ImageScaleModeSelector(
                    currentMode = state.preference.imageScaleMode,
                    onModeSelected = { mode ->
                        viewModel.onIntent(ViewerIntent.SetImageScaleMode(mode))
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Row 3: [현재 페이지] ── slider ── [총 페이지]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val currentPageLabel = if (isDoublePage && state.totalPages > 1) {
                        val spread = pageToSpread(state.currentPage)
                        val (left, right) = spreadToPages(spread, state.totalPages)
                        if (right != null) "${left + 1}-${right + 1}" else "${left + 1}"
                    } else {
                        "${state.currentPage + 1}"
                    }
                    Text(
                        text = currentPageLabel,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 32.dp),
                        textAlign = TextAlign.Center,
                    )
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
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(
                        text = "${state.totalPages}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 32.dp),
                        textAlign = TextAlign.Center,
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
                text = stringResource(Res.string.viewer_area_translate),
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
                text = stringResource(Res.string.viewer_page_translate),
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
        stringResource(Res.string.pipeline_detecting) to PipelineStage.DETECTING,
        stringResource(Res.string.pipeline_cropping) to PipelineStage.CROPPING,
        stringResource(Res.string.pipeline_recognizing) to PipelineStage.RECOGNIZING,
        stringResource(Res.string.pipeline_translating) to PipelineStage.TRANSLATING,
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
                    text = if (isTranslating) stringResource(Res.string.viewer_translating) else stringResource(Res.string.viewer_translation_pending),
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
                text = stringResource(Res.string.viewer_show_translation),
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
    globalOverlayEnabled: Boolean,
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
                        gestures = if (!isAreaSelectMode) EnabledZoomGestures.ZoomAndPan else EnabledZoomGestures.None,
                        onClick = if (!isAreaSelectMode && !isTranslationActive) {
                            { offset ->
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

                    // Bounding Box 하이라이트 (오버레이 표시 중에는 숨김)
                    if (ocrResults.isNotEmpty() && !globalOverlayEnabled) {
                        BoundingBoxOverlay(
                            textBlocks = ocrResults,
                            imageWidth = overlayWidth,
                            imageHeight = overlayHeight,
                            imageScaleMode = ImageScaleMode.AUTO_FIT,
                            onBlockTap = onTextBlockTap,
                            modifier = overlayZoomModifier(overlayWidth, overlayHeight),
                        )
                    }

                    // 번역 오버레이
                    if (globalOverlayEnabled && translationResults.isNotEmpty() && ocrResults.isNotEmpty()) {
                        TranslationOverlay(
                            textBlocks = ocrResults,
                            translationResults = translationResults,
                            dominantColors = overlayColors,
                            imageWidth = overlayWidth,
                            imageHeight = overlayHeight,
                            imageScaleMode = ImageScaleMode.AUTO_FIT,
                            modifier = overlayZoomModifier(overlayWidth, overlayHeight),
                        )
                    }

                    // 캐시된 말풍선 오버레이
                    if (cachedBlocks.isNotEmpty()) {
                        // globalOverlayEnabled → 전체 블록 표시, 아니면 개별 탭 방식
                        val indices = if (globalOverlayEnabled) {
                            remember(cachedBlocks) { cachedBlocks.indices.toSet() }
                        } else {
                            visibleCachedBlockIndices
                        }
                        if (indices.isNotEmpty()) {
                            val (visibleTextBlocks, visibleTranslations, visibleColors) = remember(
                                indices, cachedBlocks, cachedOverlayColors,
                            ) {
                                val sorted = indices.sorted()
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
                            if (ocrResults.isNotEmpty() && !globalOverlayEnabled) {
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
                            if (globalOverlayEnabled && translationResults.isNotEmpty() && ocrResults.isNotEmpty()) {
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

                            // 캐시된 말풍선 오버레이
                            if (cachedBlocks.isNotEmpty()) {
                                val indices = if (globalOverlayEnabled) {
                                    remember(cachedBlocks) { cachedBlocks.indices.toSet() }
                                } else {
                                    visibleCachedBlockIndices
                                }
                                if (indices.isNotEmpty()) {
                                    val (visibleTextBlocks, visibleTranslations, visibleColors) = remember(
                                        indices, cachedBlocks, cachedOverlayColors,
                                    ) {
                                        val sorted = indices.sorted()
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

@Composable
private fun ImageScaleMode.displayLabel(): String = when (this) {
    ImageScaleMode.FIT_WIDTH -> stringResource(Res.string.scale_fit_width)
    ImageScaleMode.FIT_HEIGHT -> stringResource(Res.string.scale_fit_height)
    ImageScaleMode.AUTO_FIT -> stringResource(Res.string.scale_auto_fit)
    ImageScaleMode.ORIGINAL -> stringResource(Res.string.scale_original)
}

@Composable
private fun ImageScaleModeSelector(
    currentMode: ImageScaleMode,
    onModeSelected: (ImageScaleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .padding(2.dp),
    ) {
        ImageScaleMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) OcrButtonColor.copy(alpha = 0.4f)
                        else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.displayLabel(),
                    color = if (isSelected) OcrButtonColor else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AutoProcessStatusIndicator(
    mode: AutoProcessMode,
    status: AutoProcessStatus,
    hasCachedTranslation: Boolean = false,
    hasCachedOcr: Boolean = false,
    onRetry: () -> Unit,
) {
    // 번역 안함 모드에서도 캐시가 있으면 완료 상태 표시
    if (mode == AutoProcessMode.NONE) {
        if (hasCachedTranslation) {
            PulsingStatusText(stringResource(Res.string.status_translate_done), BoundingBoxColor)
        } else if (hasCachedOcr) {
            PulsingStatusText(stringResource(Res.string.status_ocr_done), BoundingBoxColor)
        }
        return
    }

    when (status) {
        AutoProcessStatus.OCR_PROCESSING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = OcrButtonColor,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.status_ocr_processing), color = OcrButtonColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        AutoProcessStatus.OCR_DONE -> {
            if (mode == AutoProcessMode.AUTO_OCR) {
                PulsingStatusText(stringResource(Res.string.status_ocr_done), BoundingBoxColor)
            }
            // AUTO_TRANSLATE 모드에서는 OCR_DONE이 일시적 — 곧 번역 시작
        }
        AutoProcessStatus.OCR_FAILED -> {
            Text(
                text = stringResource(Res.string.status_ocr_failed),
                color = Color(0xFFEF4444),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRetry() },
            )
        }
        AutoProcessStatus.TRANSLATE_PROCESSING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = OcrButtonColor,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.status_translate_processing), color = OcrButtonColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        AutoProcessStatus.TRANSLATE_DONE -> {
            PulsingStatusText(stringResource(Res.string.status_translate_done), BoundingBoxColor)
        }
        AutoProcessStatus.TRANSLATE_FAILED -> {
            Text(
                text = stringResource(Res.string.status_translate_failed),
                color = Color(0xFFEF4444),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRetry() },
            )
        }
        AutoProcessStatus.IDLE -> {}
    }
}

@Composable
private fun PulsingStatusText(text: String, color: Color) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color.copy(alpha = alpha), RoundedCornerShape(50)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = color.copy(alpha = alpha),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AutoProcessModeDialog(
    currentMode: AutoProcessMode,
    onModeSelected: (AutoProcessMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_auto_process_title)) },
        text = {
            Column {
                AutoProcessMode.entries.forEach { mode ->
                    val modeLabel = when (mode) {
                        AutoProcessMode.NONE -> stringResource(Res.string.auto_process_none)
                        AutoProcessMode.AUTO_OCR -> stringResource(Res.string.auto_process_ocr)
                        AutoProcessMode.AUTO_TRANSLATE -> stringResource(Res.string.auto_process_translate)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onModeSelected(mode)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = {
                                onModeSelected(mode)
                                onDismiss()
                            },
                        )
                        Text(
                            text = modeLabel,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        },
    )
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
        title = { Text(stringResource(Res.string.dialog_view_settings_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.dialog_reading_direction),
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
                                ReadingDirection.LTR -> stringResource(Res.string.reading_direction_ltr)
                                ReadingDirection.RTL -> stringResource(Res.string.reading_direction_rtl)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.dialog_view_mode),
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
                                ViewMode.SINGLE_PAGE -> stringResource(Res.string.view_mode_single)
                                ViewMode.DOUBLE_PAGE -> stringResource(Res.string.view_mode_double)
                                ViewMode.WEBTOON -> stringResource(Res.string.view_mode_webtoon_full)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

// ── 선택 번역 바텀시트 ──────────────────────────────────────────

@Composable
private fun SelectedTranslationBottomSheet(
    translations: List<Pair<String, String>>,
    isTranslating: Boolean,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .background(
                color = Color(0xFF1A1A1A),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .pointerInput(Unit) { detectTapGestures { /* 터치 소비 — 외부 dismiss 방지 */ } }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 드래그 핸들 (터치 영역 확보를 위해 전체 너비 Row로 감쌈)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
            )
        }

        if (isTranslating) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                translations.forEachIndexed { index, (original, translated) ->
                    Column {
                        Text(
                            text = original,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = translated,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (index < translations.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}
