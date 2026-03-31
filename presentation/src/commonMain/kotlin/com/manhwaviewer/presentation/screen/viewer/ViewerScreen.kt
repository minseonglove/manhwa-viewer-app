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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.presentation.util.toImageBitmap
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.ic_arrow_back
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

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

@Composable
fun ViewerScreen(
    archiveId: Long,
    startPage: Int = 0,
    sortByDate: Boolean = false,
    sortAscending: Boolean = true,
    onBack: () -> Unit,
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

    val pagerState = rememberPagerState(
        initialPage = state.currentPage,
        pageCount = { state.totalPages },
    )

    LaunchedEffect(pagerState.currentPage) {
        viewModel.onIntent(ViewerIntent.SetPage(pagerState.currentPage))
    }

    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage && state.totalPages > 0) {
            pagerState.animateScrollToPage(state.currentPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        } else if (state.totalPages > 0) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = state.preference.readingDirection == ReadingDirection.RTL,
            ) { page ->
                val imageBytes = produceState<ByteArray?>(null, page) {
                    value = viewModel.getImageBytes(page)
                }
                ZoomableImage(
                    imageBytes = imageBytes.value,
                    page = page,
                    keepZoomState = state.preference.keepZoomState,
                    isAreaSelectMode = state.translationMode == TranslationMode.AREA_SELECT,
                    cropRect = if (page == state.currentPage) state.cropRect else null,
                    ocrResults = if (page == state.currentPage) state.ocrResults else emptyList(),
                    pagerSettledPage = pagerState.settledPage,
                    onTapLeft = { viewModel.onIntent(ViewerIntent.PreviousPage) },
                    onTapCenter = { viewModel.onIntent(ViewerIntent.ToggleControls) },
                    onTapRight = { viewModel.onIntent(ViewerIntent.NextPage) },
                    onUpdateCropRect = { rect ->
                        viewModel.onIntent(ViewerIntent.UpdateCropRect(rect))
                    },
                    onConfirmCrop = { viewModel.onIntent(ViewerIntent.ConfirmCrop) },
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
                        // Show this block's text in bottom sheet
                        viewModel.onIntent(ViewerIntent.ConfirmCrop)
                    },
                )
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

        // 선택 영역 크롭 오버레이 (전체 화면 레벨)
        if (state.translationMode == TranslationMode.AREA_SELECT && state.cropRect != null) {
            CropConfirmButton(
                cropRect = state.cropRect!!,
                onConfirm = { viewModel.onIntent(ViewerIntent.ConfirmCrop) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // 상단 헤더
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OverlayBackground)
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
                        text = state.archive?.fileName ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // 번역 버튼 - 토글 방식
                    Box(
                        modifier = Modifier
                            .background(
                                color = when {
                                    state.isOcrProcessing -> OcrButtonColor.copy(alpha = 0.5f)
                                    state.translationMode != TranslationMode.NONE -> OcrButtonColor
                                    else -> OcrButtonColor
                                },
                                shape = RoundedCornerShape(8.dp),
                            )
                            .then(
                                if (state.translationMode != TranslationMode.NONE) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .then(
                                if (!state.isOcrProcessing) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = {
                                            if (state.translationMode != TranslationMode.NONE) {
                                                viewModel.onIntent(
                                                    ViewerIntent.SetTranslationMode(TranslationMode.NONE)
                                                )
                                            } else {
                                                viewModel.onIntent(
                                                    ViewerIntent.SetTranslationMode(TranslationMode.AREA_SELECT)
                                                )
                                            }
                                        })
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                state.isOcrProcessing -> "OCR..."
                                state.translationMode != TranslationMode.NONE -> "번역 ✕"
                                else -> "번역"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 설정 아이콘
                    IconButton(onClick = { /* 설정 화면 연동 예정 */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = Color.White,
                        )
                    }
                }

                // 번역 옵션 바
                AnimatedVisibility(
                    visible = state.translationMode != TranslationMode.NONE || state.showControls,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    if (state.translationMode != TranslationMode.NONE) {
                        TranslationOptionBar(
                            currentMode = state.translationMode,
                            onAreaSelect = {
                                viewModel.onIntent(
                                    ViewerIntent.SetTranslationMode(TranslationMode.AREA_SELECT)
                                )
                            },
                            onPageTranslate = {
                                viewModel.onIntent(ViewerIntent.RunPageOcr)
                            },
                        )
                    }
                }
            }
        }

        // 하단 컨트롤
        AnimatedVisibility(
            visible = state.showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OverlayBackground)
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
                        text = "${state.currentPage + 1} / ${state.totalPages}",
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
                            text = if (state.preference.readingDirection == ReadingDirection.RTL) "RTL \u2190" else "LTR \u2192",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                    }
                }

                // 2행: 슬라이더
                if (state.totalPages > 1) {
                    Slider(
                        value = state.currentPage.toFloat(),
                        onValueChange = { viewModel.onIntent(ViewerIntent.SetPage(it.toInt())) },
                        valueRange = 0f..(state.totalPages - 1).toFloat(),
                        steps = state.totalPages - 2,
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

        // OCR 결과 바텀시트
        AnimatedVisibility(
            visible = state.ocrBottomSheetText != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            OcrBottomSheet(
                text = state.ocrBottomSheetText ?: "",
                onDismiss = { viewModel.onIntent(ViewerIntent.DismissOcrResult) },
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
) {
    Row(
        modifier = Modifier
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
                    color = PageTranslateBg,
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = PageTranslateBorder,
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

// ── 크롭 확정 버튼 ───────────────────────────────────────────

@Composable
private fun CropConfirmButton(
    cropRect: CropRect,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val centerX = (cropRect.startX + cropRect.endX) / 2f
    val bottomY = maxOf(cropRect.startY, cropRect.endY)

    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = (centerX - 40).coerceAtLeast(0f).dp,
                    y = (bottomY + 8).dp,
                )
                .background(
                    color = AreaSelectBorder,
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable { onConfirm() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "\u2702 OCR 실행",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── OCR 결과 바텀시트 ─────────────────────────────────────────

@Composable
private fun OcrBottomSheet(
    text: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // 드래그 핸들
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .background(
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(2.dp),
                )
                .align(Alignment.CenterHorizontally),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 원문 텍스트
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 번역 결과 (TODO)
        Text(
            text = "\u2192 번역 결과 (TODO)",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(6.dp))

        // OCR 엔진 배지
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
        }
    }
}

// ── ZoomableImage (telephoto + keepZoomState + 크롭 오버레이 + BB 하이라이트) ──

@Composable
private fun ZoomableImage(
    imageBytes: ByteArray?,
    page: Int,
    keepZoomState: Boolean,
    isAreaSelectMode: Boolean,
    cropRect: CropRect?,
    ocrResults: List<TextBlock>,
    pagerSettledPage: Int,
    onTapLeft: () -> Unit,
    onTapCenter: () -> Unit,
    onTapRight: () -> Unit,
    onUpdateCropRect: (CropRect) -> Unit,
    onConfirmCrop: () -> Unit,
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
            .then(
                if (!isAreaSelectMode) {
                    Modifier.zoomable(
                        state = zoomableState,
                        onClick = { offset ->
                            when {
                                offset.x < containerWidth / 3f -> onTapLeft()
                                offset.x > containerWidth * 2f / 3f -> onTapRight()
                                else -> onTapCenter()
                            }
                        },
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageBytes != null) {
            Image(
                bitmap = imageBytes.toImageBitmap(),
                contentDescription = "Page ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            // Bounding Box 하이라이트
            if (ocrResults.isNotEmpty()) {
                BoundingBoxOverlay(
                    textBlocks = ocrResults,
                    onBlockTap = onTextBlockTap,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 선택 영역 크롭 오버레이
            if (isAreaSelectMode) {
                CropOverlay(
                    cropRect = cropRect,
                    onUpdateCropRect = onUpdateCropRect,
                    onConfirmCrop = onConfirmCrop,
                    modifier = Modifier.fillMaxSize(),
                )
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
    onBlockTap: (TextBlock) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)

    Canvas(
        modifier = modifier
            .pointerInput(textBlocks) {
                detectTapGestures { offset ->
                    // 탭한 위치가 어떤 TextBlock 안에 있는지 확인
                    textBlocks.firstOrNull { block ->
                        offset.x >= block.x && offset.x <= block.x + block.width &&
                            offset.y >= block.y && offset.y <= block.y + block.height
                    }?.let { onBlockTap(it) }
                }
            },
    ) {
        textBlocks.forEach { block ->
            drawRect(
                color = BoundingBoxColor,
                topLeft = Offset(block.x.toFloat(), block.y.toFloat()),
                size = Size(block.width.toFloat(), block.height.toFloat()),
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
    onConfirmCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragStart by remember { mutableStateOf(Offset.Zero) }

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
    val handleSize = 8f

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        onUpdateCropRect(
                            CropRect(
                                startX = offset.x,
                                startY = offset.y,
                                endX = offset.x,
                                endY = offset.y,
                            )
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        onUpdateCropRect(
                            CropRect(
                                startX = dragStart.x,
                                startY = dragStart.y,
                                endX = pos.x,
                                endY = pos.y,
                            )
                        )
                    },
                    onDragEnd = {
                        // 크롭 영역이 충분히 큰 경우 자동 확정
                        if (cropRect != null) {
                            val w = abs(cropRect.endX - cropRect.startX)
                            val h = abs(cropRect.endY - cropRect.startY)
                            if (w > 20f && h > 20f) {
                                onConfirmCrop()
                            }
                        }
                    },
                )
            },
    ) {
        // 반투명 오버레이 + 크롭 사각형
        if (cropRect != null) {
            val minX = minOf(cropRect.startX, cropRect.endX)
            val minY = minOf(cropRect.startY, cropRect.endY)
            val maxX = maxOf(cropRect.startX, cropRect.endX)
            val maxY = maxOf(cropRect.startY, cropRect.endY)
            val rectWidth = maxX - minX
            val rectHeight = maxY - minY

            Canvas(modifier = Modifier.fillMaxSize()) {
                val dimColor = Color.Black.copy(alpha = 0.3f)

                // 크롭 영역 주변 4개 직사각형으로 딤 처리
                // 상단
                drawRect(dimColor, Offset.Zero, Size(size.width, minY))
                // 하단
                drawRect(dimColor, Offset(0f, maxY), Size(size.width, size.height - maxY))
                // 좌측
                drawRect(dimColor, Offset(0f, minY), Size(minX, rectHeight))
                // 우측
                drawRect(dimColor, Offset(maxX, minY), Size(size.width - maxX, rectHeight))

                // 점선 테두리
                drawRect(
                    color = AreaSelectBorder,
                    topLeft = Offset(minX, minY),
                    size = Size(rectWidth, rectHeight),
                    style = Stroke(
                        width = 2f,
                        pathEffect = dashEffect,
                    ),
                )

                // 4개 코너 핸들
                val corners = listOf(
                    Offset(minX, minY),
                    Offset(maxX, minY),
                    Offset(minX, maxY),
                    Offset(maxX, maxY),
                )
                corners.forEach { corner ->
                    drawRect(
                        color = AreaSelectBorder,
                        topLeft = Offset(corner.x - handleSize, corner.y - handleSize),
                        size = Size(handleSize * 2, handleSize * 2),
                    )
                }
            }

            // 크롭 영역 내부 텍스트
            if (rectWidth > 60f && rectHeight > 30f) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (minX + rectWidth / 2 - 30).coerceAtLeast(0f).dp,
                            y = (minY + rectHeight / 2 - 8).coerceAtLeast(0f).dp,
                        ),
                ) {
                    Text(
                        text = "\u2702 선택 영역",
                        color = AreaSelectText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
