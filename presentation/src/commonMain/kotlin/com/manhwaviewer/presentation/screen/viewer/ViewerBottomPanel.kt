package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import manhwa_viewer.presentation.generated.resources.view_mode_double
import manhwa_viewer.presentation.generated.resources.view_mode_single
import manhwa_viewer.presentation.generated.resources.view_mode_webtoon
import com.manhwaviewer.presentation.theme.MangaLensColors
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

private enum class BottomPanelTab { VIEW, SCALE, TRANSLATION }

@Composable
fun ViewerBottomPanel(
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
    onPageChange: (Int) -> Unit,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onSetViewMode: (ViewMode) -> Unit,
    onSetImageScaleMode: (ImageScaleMode) -> Unit,
    onSetAutoProcessMode: (AutoProcessMode) -> Unit,
    onRetryAutoProcess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(BottomPanelTab.VIEW) }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(PanelBackground, RoundedCornerShape(24.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // ── Progress Slider ──
        ProgressSlider(
            currentPagerPage = currentPagerPage,
            pagerPageCount = pagerPageCount,
            currentPage = currentPage,
            totalPages = totalPages,
            isDoublePage = isDoublePage,
            onPageChange = onPageChange,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tab Content ──
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "tab_content",
        ) { tab ->
            when (tab) {
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
                    onSetAutoProcessMode = onSetAutoProcessMode,
                    onRetry = onRetryAutoProcess,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Tab Navigation Bar ──
        HorizontalDivider(color = MangaLensColors.separatorLight)
        Spacer(modifier = Modifier.height(14.dp))
        TabNavigationBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )
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
    onPageChange: (Int) -> Unit,
) {
    if (totalPages <= 1) return

    val pageLabel = if (isDoublePage) {
        val pages = spreadToPages(currentPagerPage, totalPages)
        if (pages.second != null) "${pages.first + 1}-${pages.second!! + 1}" else "${pages.first + 1}"
    } else {
        "${currentPage + 1}"
    }

    val range = 0f..(pagerPageCount - 1).coerceAtLeast(1).toFloat()

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
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = pageLabel, color = T2, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(text = "$totalPages", color = T2, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
                text = "읽기 방향",
                color = T2,
                fontSize = 10.sp,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.width(10.dp))
            SegmentedControl(
                options = listOf("LTR", "RTL"),
                selectedIndex = if (readingDirection == ReadingDirection.LTR) 0 else 1,
                onSelect = {
                    onSetReadingDirection(if (it == 0) ReadingDirection.LTR else ReadingDirection.RTL)
                },
            )
        }

        // View mode
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "뷰 모드",
                color = T2,
                fontSize = 10.sp,
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
        "가로맞춤 - 화면 너비에 맞춤",
        "세로맞춤 - 화면 높이에 맞춤",
        "자동 - 이미지 비율에 따라 최적 맞춤",
        "원본 - 원본 크기로 표시",
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
                .background(MangaLensColors.fillQuaternary, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
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
                fontSize = 10.sp,
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
            modifier = Modifier.padding(start = 4.dp),
        ) {
            TranslationStatusChip(
                status = autoProcessStatus,
                hasCachedTranslation = hasCachedTranslation,
                hasCachedOcr = hasCachedOcr,
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
    onRetry: () -> Unit,
) {
    when {
        status == AutoProcessStatus.OCR_FAILED -> {
            StatusChip(label = stringResource(Res.string.status_ocr_failed), color = Red, bg = RedBg)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "다시 시도",
                color = BlueHi,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
        status == AutoProcessStatus.TRANSLATE_FAILED -> {
            StatusChip(label = stringResource(Res.string.status_translate_failed), color = Red, bg = RedBg)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "다시 시도",
                color = BlueHi,
                fontSize = 12.sp,
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
        // idle - no chip
    }
}

@Composable
private fun StatusChip(label: String, color: Color, bg: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(bg, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
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
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
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
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isActive) BlueHi else T2,
                    fontSize = 11.sp,
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
            label = "보기",
            isActive = selectedTab == BottomPanelTab.VIEW,
            onClick = { onTabSelected(BottomPanelTab.VIEW) },
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon = { tint -> Icon(painterResource(Res.drawable.ic_view_grid), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
            label = "비율",
            isActive = selectedTab == BottomPanelTab.SCALE,
            onClick = { onTabSelected(BottomPanelTab.SCALE) },
            modifier = Modifier.weight(1f),
        )
        TabItem(
            icon = { tint -> Icon(painterResource(Res.drawable.ic_translate), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
            label = "번역",
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
            .padding(vertical = 4.dp)
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
            fontSize = 10.sp,
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
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else T2,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
