package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.ViewMode
import manhwa_viewer.presentation.generated.resources.Res
import manhwa_viewer.presentation.generated.resources.auto_process_none
import manhwa_viewer.presentation.generated.resources.auto_process_ocr
import manhwa_viewer.presentation.generated.resources.auto_process_translate
import manhwa_viewer.presentation.generated.resources.ic_book
import manhwa_viewer.presentation.generated.resources.ic_translate
import manhwa_viewer.presentation.generated.resources.ic_view_grid
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
import manhwa_viewer.presentation.generated.resources.status_untranslated
import manhwa_viewer.presentation.generated.resources.view_mode_double
import manhwa_viewer.presentation.generated.resources.view_mode_single
import manhwa_viewer.presentation.generated.resources.view_mode_webtoon
import manhwa_viewer.presentation.generated.resources.tab_view
import manhwa_viewer.presentation.generated.resources.tab_scale
import manhwa_viewer.presentation.generated.resources.tab_translation
import manhwa_viewer.presentation.generated.resources.label_reading_direction
import manhwa_viewer.presentation.generated.resources.label_view_mode
import manhwa_viewer.presentation.generated.resources.reading_direction_ltr_short
import manhwa_viewer.presentation.generated.resources.reading_direction_rtl_short
import manhwa_viewer.presentation.generated.resources.scale_fit_width_desc
import manhwa_viewer.presentation.generated.resources.scale_fit_height_desc
import manhwa_viewer.presentation.generated.resources.scale_auto_fit_desc
import manhwa_viewer.presentation.generated.resources.scale_original_desc
import manhwa_viewer.presentation.generated.resources.retry
import com.manhwaviewer.presentation.theme.MangaLensColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import coil3.compose.AsyncImage
import manhwa_viewer.presentation.generated.resources.ic_chevron_left
import manhwa_viewer.presentation.generated.resources.ic_chevron_right
import manhwa_viewer.presentation.generated.resources.next_volume
import manhwa_viewer.presentation.generated.resources.previous_volume
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ── Colors (from MangaLensColors design tokens) ──
private val PanelBackground = MangaLensColors.surfaceGlass
private val PanelBorder = MangaLensColors.separator
private val BlueHi = MangaLensColors.blueHi
private val BlueBg = MangaLensColors.blueBg
private val Green = MangaLensColors.green
private val GreenDark = MangaLensColors.greenBg
private val Red = MangaLensColors.red
private val RedBg = MangaLensColors.redBg
private val T2 = MangaLensColors.textSecondary
private val SegBg = MangaLensColors.fillSecondary
private val SegActive = MangaLensColors.surface1
private val PipelineGreen = MangaLensColors.emerald
private val PipelineOrange = MangaLensColors.amber
private val GrayText = MangaLensColors.textSecondary
private val GrayBg = MangaLensColors.fillTertiary

@Composable
fun ViewerBottomPanel(
    hazeState: HazeState,
    currentPage: Int,
    totalPages: Int,
    isDoublePage: Boolean,
    pagerPageCount: Int,
    currentPagerPage: Int,
    readingDirection: ReadingDirection,
    viewMode: ViewMode,
    imageScaleMode: ImageScaleMode,
    autoProcessMode: AutoProcessMode,
    autoProcessStatus: AutoProcessStatus,
    pipelineStage: PipelineStage,
    hasCachedTranslation: Boolean,
    hasCachedOcr: Boolean,
    cacheCheckCompleted: Boolean,
    selectedTab: BottomPanelTab,
    previousVolumeName: String?,
    previousVolumeThumbnailModel: Any?,
    nextVolumeName: String?,
    nextVolumeThumbnailModel: Any?,
    onPageChange: (Int) -> Unit,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onSetImageScaleMode: (ImageScaleMode) -> Unit,
    onSetAutoProcessMode: (AutoProcessMode) -> Unit,
    onRetryAutoProcess: () -> Unit,
    onTabSelected: (BottomPanelTab) -> Unit,
    onNavigateToPreviousVolume: () -> Unit,
    onNavigateToNextVolume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val dismissThresholdPx = with(density) { 80.dp.toPx() }
    val animatedOffsetDp by animateDpAsState(
        targetValue = with(density) { dragOffsetPx.toDp() },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
    )

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .widthIn(max = 480.dp)
            .offset(y = animatedOffsetDp)
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MangaLensColors.surface1,
                    tints = listOf(HazeTint(PanelBackground)),
                    blurRadius = 20.dp,
                    noiseFactor = 0.15f,
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetPx > dismissThresholdPx) {
                            onDismiss()
                        }
                        dragOffsetPx = 0f
                    },
                    onDragCancel = { dragOffsetPx = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                    },
                )
            },
    ) {
        // ── Handle (바텀시트 스타일) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp),
        ) {
        // ── Progress Slider ──
        ProgressSlider(
            currentPagerPage = currentPagerPage,
            pagerPageCount = pagerPageCount,
            currentPage = currentPage,
            totalPages = totalPages,
            isDoublePage = isDoublePage,
            previousVolumeName = previousVolumeName,
            previousVolumeThumbnailModel = previousVolumeThumbnailModel,
            nextVolumeName = nextVolumeName,
            nextVolumeThumbnailModel = nextVolumeThumbnailModel,
            onPageChange = onPageChange,
            onNavigateToPreviousVolume = onNavigateToPreviousVolume,
            onNavigateToNextVolume = onNavigateToNextVolume,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tab Content ──
        var targetHeight by remember { mutableStateOf(0.dp) }
        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (targetHeight > 0.dp) Modifier.height(animatedHeight) else Modifier)
                .clip(RoundedCornerShape(0.dp)),
        ) {
            Box(
                modifier = Modifier
                    .wrapContentHeight(unbounded = true)
                    .onSizeChanged { size ->
                        with(density) { targetHeight = size.height.toDp() }
                    },
            ) {
                when (selectedTab) {
                    BottomPanelTab.VIEW -> ViewTabContent(
                        readingDirection = readingDirection,
                        viewMode = viewMode,
                        onSetReadingDirection = onSetReadingDirection,
                        onSetViewMode = onSetViewMode,
                    )
                    BottomPanelTab.SCALE -> ScaleTabContent(
                        currentMode = imageScaleMode,
                        onModeSelected = onSetImageScaleMode,
                    )
                    BottomPanelTab.TRANSLATION -> TranslationTabContent(
                        autoProcessMode = autoProcessMode,
                        autoProcessStatus = autoProcessStatus,
                        pipelineStage = pipelineStage,
                        hasCachedTranslation = hasCachedTranslation,
                        hasCachedOcr = hasCachedOcr,
                        cacheCheckCompleted = cacheCheckCompleted,
                        onSetAutoProcessMode = onSetAutoProcessMode,
                        onRetry = onRetryAutoProcess,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tab Navigation Bar ──
        HorizontalDivider(color = MangaLensColors.separatorLight)
        Spacer(modifier = Modifier.height(14.dp))
        TabNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
        )
        } // inner Column (padding)
    }
}

// ══════════════════════════════════════════════════════════════════
// Progress Slider
// ══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressSlider(
    currentPagerPage: Int,
    pagerPageCount: Int,
    currentPage: Int,
    totalPages: Int,
    isDoublePage: Boolean,
    previousVolumeName: String?,
    previousVolumeThumbnailModel: Any?,
    nextVolumeName: String?,
    nextVolumeThumbnailModel: Any?,
    onPageChange: (Int) -> Unit,
    onNavigateToPreviousVolume: () -> Unit,
    onNavigateToNextVolume: () -> Unit,
) {
    if (totalPages <= 1) return

    val pageLabel = if (isDoublePage) {
        val pages = spreadToPages(currentPagerPage, totalPages)
        if (pages.second != null) "${pages.first + 1}-${pages.second!! + 1}" else "${pages.first + 1}"
    } else {
        "${currentPage + 1}"
    }

    // 이전 권 / 다음 권 힌트 행
    if (previousVolumeName != null || nextVolumeName != null) {
        VolumeHintRow(
            previousVolumeName = previousVolumeName,
            previousVolumeThumbnailModel = previousVolumeThumbnailModel,
            onNavigateToPreviousVolume = onNavigateToPreviousVolume,
            nextVolumeName = nextVolumeName,
            nextVolumeThumbnailModel = nextVolumeThumbnailModel,
            onNavigateToNextVolume = onNavigateToNextVolume,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    val range = 0f..(pagerPageCount - 1).coerceAtLeast(1).toFloat()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Slider(
            value = currentPagerPage.toFloat(),
            onValueChange = { onPageChange(it.toInt()) },
            valueRange = range,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .shadow(3.dp, CircleShape)
                        .background(Color.White, CircleShape),
                )
            },
            track = { sliderState ->
                val fraction = if (range.endInclusive > range.start) {
                    ((sliderState.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                ) {
                    Box(Modifier.fillMaxSize().background(MangaLensColors.fillTrack))
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White, RoundedCornerShape(2.dp)))
                }
            },
            modifier = Modifier.weight(1f).height(32.dp),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().offset(y = (-9).dp).padding(horizontal = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = pageLabel, color = T2, style = MaterialTheme.typography.labelSmall)
        Text(text = "$totalPages", color = T2, style = MaterialTheme.typography.labelSmall)
    }
}

// ══════════════════════════════════════════════════════════════════
// Volume Hint Row (이전 권 / 다음 권)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun VolumeHintRow(
    previousVolumeName: String?,
    previousVolumeThumbnailModel: Any?,
    onNavigateToPreviousVolume: () -> Unit,
    nextVolumeName: String?,
    nextVolumeThumbnailModel: Any?,
    onNavigateToNextVolume: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (previousVolumeName != null) {
            PreviousVolumeHint(
                name = previousVolumeName,
                thumbnailModel = previousVolumeThumbnailModel,
                onClick = onNavigateToPreviousVolume,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (nextVolumeName != null) {
            NextVolumeHint(
                name = nextVolumeName,
                thumbnailModel = nextVolumeThumbnailModel,
                onClick = onNavigateToNextVolume,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviousVolumeHint(
    name: String,
    thumbnailModel: Any?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(end = 4.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_left),
            contentDescription = null,
            tint = MangaLensColors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        if (thumbnailModel != null) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Column(modifier = Modifier.widthIn(max = 80.dp)) {
            Text(
                text = stringResource(Res.string.previous_volume),
                color = MangaLensColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = MaterialTheme.typography.labelSmall.fontSize,
            )
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
                lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.3,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NextVolumeHint(
    name: String,
    thumbnailModel: Any?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = 4.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 80.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(Res.string.next_volume),
                color = MangaLensColors.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = MaterialTheme.typography.labelSmall.fontSize,
            )
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
                lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.3,
            )
        }
        if (thumbnailModel != null) {
            Spacer(modifier = Modifier.width(5.dp))
            AsyncImage(
                model = thumbnailModel,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            painter = painterResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MangaLensColors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// View Tab
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ViewTabContent(
    readingDirection: ReadingDirection,
    viewMode: ViewMode,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Reading direction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.label_reading_direction),
                color = T2,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.width(10.dp))
            SegmentedControl(
                options = listOf(
                    stringResource(Res.string.reading_direction_ltr_short),
                    stringResource(Res.string.reading_direction_rtl_short),
                ),
                selectedIndex = if (readingDirection == ReadingDirection.LTR) 0 else 1,
                onSelect = {
                    onSetReadingDirection(if (it == 0) ReadingDirection.LTR else ReadingDirection.RTL)
                },
            )
        }

        // View mode
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.label_view_mode),
                color = T2,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.width(10.dp))
            SegmentedControl(
                options = listOf(
                    stringResource(Res.string.view_mode_single),
                    stringResource(Res.string.view_mode_double),
                    stringResource(Res.string.view_mode_webtoon),
                ),
                selectedIndex = ViewMode.entries.indexOf(viewMode),
                onSelect = { onSetViewMode(ViewMode.entries[it]) },
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Scale Tab
// ══════════════════════════════════════════════════════════════════

@Composable
private fun ScaleTabContent(
    currentMode: ImageScaleMode,
    onModeSelected: (ImageScaleMode) -> Unit,
) {
    val labels = listOf(
        stringResource(Res.string.scale_fit_width),
        stringResource(Res.string.scale_fit_height),
        stringResource(Res.string.scale_auto_fit),
        stringResource(Res.string.scale_original),
    )
    val descriptions = listOf(
        stringResource(Res.string.scale_fit_width_desc),
        stringResource(Res.string.scale_fit_height_desc),
        stringResource(Res.string.scale_auto_fit_desc),
        stringResource(Res.string.scale_original_desc),
    )
    val selectedIndex = ImageScaleMode.entries.indexOf(currentMode)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedControl(
            options = labels,
            selectedIndex = selectedIndex,
            onSelect = { onModeSelected(ImageScaleMode.entries[it]) },
            fullWidth = true,
        )
        // Info chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MangaLensColors.fillQuaternary, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(BlueHi, RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = descriptions[selectedIndex],
                color = T2,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Translation Tab
// ══════════════════════════════════════════════════════════════════

@Composable
private fun TranslationTabContent(
    autoProcessMode: AutoProcessMode,
    autoProcessStatus: AutoProcessStatus,
    pipelineStage: PipelineStage,
    hasCachedTranslation: Boolean,
    hasCachedOcr: Boolean,
    cacheCheckCompleted: Boolean,
    onSetAutoProcessMode: (AutoProcessMode) -> Unit,
    onRetry: () -> Unit,
) {
    val isFailed = autoProcessStatus == AutoProcessStatus.OCR_FAILED ||
            autoProcessStatus == AutoProcessStatus.TRANSLATE_FAILED

    // 캐시 히트 시 파이프라인 단계 표시
    val effectiveStage = when {
        hasCachedTranslation -> PipelineStage.DONE
        hasCachedOcr -> PipelineStage.TRANSLATING  // OCR까지만 완료 (감지/크롭/인식 ✓, 번역 ●)
        else -> pipelineStage
    }

    val showPipeline = !isFailed && (
            autoProcessMode != AutoProcessMode.NONE &&
                    autoProcessStatus != AutoProcessStatus.IDLE ||
                    hasCachedOcr || hasCachedTranslation
            )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        // Status chip + pipeline in same row
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TranslationStatusChip(
                status = autoProcessStatus,
                hasCachedTranslation = hasCachedTranslation,
                hasCachedOcr = hasCachedOcr,
                cacheCheckCompleted = cacheCheckCompleted,
                onRetry = onRetry,
            )
            if (showPipeline) {
                Spacer(modifier = Modifier.width(7.dp))
                PipelineSteps(status = autoProcessStatus, stage = effectiveStage)
            }
        }

        // 3 mode buttons
        TranslationModeButtons(
            currentMode = autoProcessMode,
            onModeSelected = onSetAutoProcessMode,
        )
    }
}

@Composable
private fun TranslationStatusChip(
    status: AutoProcessStatus,
    hasCachedTranslation: Boolean,
    hasCachedOcr: Boolean,
    cacheCheckCompleted: Boolean,
    onRetry: () -> Unit,
) {
    when {
        status == AutoProcessStatus.OCR_FAILED -> {
            StatusChip(label = stringResource(Res.string.status_ocr_failed), color = Red, bg = RedBg)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = stringResource(Res.string.retry),
                color = BlueHi,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
        status == AutoProcessStatus.TRANSLATE_FAILED -> {
            StatusChip(label = stringResource(Res.string.status_translate_failed), color = Red, bg = RedBg)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = stringResource(Res.string.retry),
                color = BlueHi,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
        // 진행 중 — 칩으로 표시
        status == AutoProcessStatus.OCR_PROCESSING -> {
            StatusChip(label = stringResource(Res.string.status_ocr_processing), color = BlueHi, bg = BlueBg)
        }
        status == AutoProcessStatus.TRANSLATE_PROCESSING -> {
            StatusChip(label = stringResource(Res.string.status_translate_processing), color = BlueHi, bg = BlueBg)
        }
        // 완료 — hasCachedTranslation을 hasCachedOcr보다 먼저 체크
        status == AutoProcessStatus.TRANSLATE_DONE || hasCachedTranslation -> {
            StatusChip(label = stringResource(Res.string.status_translate_done), color = Green, bg = GreenDark)
        }
        status == AutoProcessStatus.OCR_DONE || hasCachedOcr -> {
            StatusChip(label = stringResource(Res.string.status_ocr_done), color = Green, bg = GreenDark)
        }
        // 캐시 확인 완료 후 번역/OCR 없음 → 미번역
        cacheCheckCompleted -> {
            StatusChip(label = stringResource(Res.string.status_untranslated), color = GrayText, bg = GrayBg)
        }
        // 캐시 확인 중 → 표시 없음
    }
}

@Composable
private fun StatusChip(label: String, color: Color, bg: Color) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(bg, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun PipelineSteps(
    status: AutoProcessStatus,
    stage: PipelineStage,
) {
    val labels = listOf(
        stringResource(Res.string.pipeline_detecting),
        stringResource(Res.string.pipeline_cropping),
        stringResource(Res.string.pipeline_recognizing),
        stringResource(Res.string.pipeline_translating),
    )
    val pipelineStages = listOf(
        PipelineStage.DETECTING,
        PipelineStage.CROPPING,
        PipelineStage.RECOGNIZING,
        PipelineStage.TRANSLATING,
    )

    // 실패한 단계 찾기
    val failedOrdinal = when (status) {
        AutoProcessStatus.OCR_FAILED -> PipelineStage.DETECTING.ordinal
        AutoProcessStatus.TRANSLATE_FAILED -> PipelineStage.TRANSLATING.ordinal
        else -> -1
    }

    var isFirstVisible = true

    Row(verticalAlignment = Alignment.CenterVertically) {
        pipelineStages.forEachIndexed { index, pipeStage ->
            val isDone = stage == PipelineStage.DONE || stage.ordinal > pipeStage.ordinal
            val isCurrent = stage.ordinal == pipeStage.ordinal
            val isFail = pipeStage.ordinal == failedOrdinal

            // 대기 상태면 표시하지 않음
            if (!isDone && !isCurrent && !isFail) return@forEachIndexed

            // 구분선 (첫 번째 항목 제외)
            if (!isFirstVisible) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = 8.dp, height = 0.5.dp)
                        .background(if (isDone) PipelineGreen else if (isFail) Red else MangaLensColors.fillPending),
                )
            }
            isFirstVisible = false

            val icon = when {
                isDone -> "\u2713"   // ✓
                isFail -> "\u2715"   // ✕
                isCurrent -> "\u25CF" // ●
                else -> ""
            }
            val color = when {
                isDone -> PipelineGreen
                isFail -> Red
                isCurrent -> PipelineOrange
                else -> T2
            }

            Text(
                text = "$icon ${labels[index]}",
                color = color,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun TranslationModeButtons(
    currentMode: AutoProcessMode,
    onModeSelected: (AutoProcessMode) -> Unit,
) {
    val modes = listOf(
        AutoProcessMode.NONE to stringResource(Res.string.auto_process_none),
        AutoProcessMode.AUTO_OCR to stringResource(Res.string.auto_process_ocr),
        AutoProcessMode.AUTO_TRANSLATE to stringResource(Res.string.auto_process_translate),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        modes.forEach { (mode, label) ->
            val isActive = mode == currentMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isActive) MangaLensColors.blueActiveBg else MangaLensColors.fillTertiary,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .border(
                        width = if (isActive) 1.dp else 0.5.dp,
                        color = if (isActive) BlueHi else MangaLensColors.borderInactive,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isActive) BlueHi else T2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// Tab Navigation Bar
// ══════════════════════════════════════════════════════════════════

@Composable
private fun TabNavigationBar(
    selectedTab: BottomPanelTab,
    onTabSelected: (BottomPanelTab) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TabItem(
            icon = { tint -> Icon(painterResource(Res.drawable.ic_book), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
            label = stringResource(Res.string.tab_view),
            isActive = selectedTab == BottomPanelTab.VIEW,
            onClick = { onTabSelected(BottomPanelTab.VIEW) },
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon = { tint -> Icon(painterResource(Res.drawable.ic_view_grid), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
            label = stringResource(Res.string.tab_scale),
            isActive = selectedTab == BottomPanelTab.SCALE,
            onClick = { onTabSelected(BottomPanelTab.SCALE) },
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon = { tint -> Icon(painterResource(Res.drawable.ic_translate), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
            label = stringResource(Res.string.tab_translation),
            isActive = selectedTab == BottomPanelTab.TRANSLATION,
            onClick = { onTabSelected(BottomPanelTab.TRANSLATION) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TabItem(
    icon: @Composable (Color) -> Unit,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (isActive) BlueHi else Color.White
    val alpha = if (isActive) 1f else 0.45f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .then(Modifier.background(Color.Transparent).let {
                if (!isActive) it else it  // alpha handled per element
            }),
    ) {
        Box(modifier = Modifier.then(if (!isActive) Modifier else Modifier)) {
            icon(color.copy(alpha = alpha))
        }
        Text(
            text = label,
            color = color.copy(alpha = alpha),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// Segmented Control (reusable)
// ══════════════════════════════════════════════════════════════════

@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fullWidth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .background(SegBg, RoundedCornerShape(10.dp))
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .then(if (fullWidth) Modifier.weight(1f) else Modifier)
                    .background(
                        color = if (isSelected) SegActive else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else T2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
