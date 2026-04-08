package com.manhwaviewer.presentation.screen.viewer

import com.manhwaviewer.domain.entity.ArchiveFile
import androidx.compose.ui.graphics.Color
import com.manhwaviewer.domain.entity.OcrBlock
import com.manhwaviewer.domain.entity.TranslationBlock
import androidx.compose.ui.geometry.Offset
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference

enum class PipelineStage {
    IDLE, DETECTING, CROPPING, RECOGNIZING, TRANSLATING, DONE
}

enum class AutoProcessMode {
    NONE,           // 번역 안함
    AUTO_OCR,       // 자동 OCR
    AUTO_TRANSLATE  // 자동 번역
}

enum class BottomPanelTab { VIEW, SCALE, TRANSLATION }

enum class AutoProcessStatus {
    IDLE,
    OCR_PROCESSING,
    OCR_DONE,
    OCR_FAILED,
    TRANSLATE_PROCESSING,
    TRANSLATE_DONE,
    TRANSLATE_FAILED,
}

data class ViewerState(
    val archive: ArchiveFile? = null,
    val filePath: String = "",
    val imageEntries: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val preference: ViewerPreference = ViewerPreference(),
    val showControls: Boolean = false,
    val isLoading: Boolean = true,
    val pipelineStage: PipelineStage = PipelineStage.IDLE,
    val globalOverlayEnabled: Boolean = false,
    val cachedBlocks: List<TranslationBlock> = emptyList(),
    val cachedImageWidth: Int = 0,
    val cachedImageHeight: Int = 0,
    val cachedOverlayColors: List<Color> = emptyList(),
    val selectedCachedBlockIndices: Set<Int> = emptySet(),
    val autoProcessMode: AutoProcessMode = AutoProcessMode.NONE,
    val autoProcessStatus: AutoProcessStatus = AutoProcessStatus.IDLE,
    val autoOcrBlocks: List<OcrBlock> = emptyList(),
    val autoOcrImageWidth: Int = 0,
    val autoOcrImageHeight: Int = 0,
    val selectedOcrBlockIndices: Set<Int> = emptySet(),
    val autoTranslationBlocks: List<TranslationBlock> = emptyList(),
    val autoTranslationImageWidth: Int = 0,
    val autoTranslationImageHeight: Int = 0,
    val autoTranslationOverlayColors: List<Color> = emptyList(),
    // 선택 번역 바텀시트
    val selectedBlockTranslations: List<Pair<String, String>> = emptyList(),
    val isSelectedTranslationSheetVisible: Boolean = false,
    val isSelectedTranslating: Boolean = false,
    val selectedBottomTab: BottomPanelTab = BottomPanelTab.VIEW,
) {
    val isOverlayButtonEnabled: Boolean
        get() = autoProcessStatus == AutoProcessStatus.OCR_DONE ||
                autoProcessStatus == AutoProcessStatus.TRANSLATE_DONE ||
                autoProcessStatus == AutoProcessStatus.TRANSLATE_PROCESSING ||
                cachedBlocks.isNotEmpty() ||
                autoTranslationBlocks.isNotEmpty()

    val hasSelectedBlocks: Boolean
        get() = selectedOcrBlockIndices.isNotEmpty() || selectedCachedBlockIndices.isNotEmpty()
}

sealed interface ViewerIntent {
    data class LoadArchive(val archiveId: Long, val startPage: Int = -1, val sortByDate: Boolean = false, val sortAscending: Boolean = true) : ViewerIntent
    data class SetPage(val page: Int) : ViewerIntent
    data object NextPage : ViewerIntent
    data object PreviousPage : ViewerIntent
    data object ToggleControls : ViewerIntent
    data object SaveProgress : ViewerIntent
    data object ToggleGlobalOverlay : ViewerIntent
    data object TranslateSelectedBlocks : ViewerIntent
    data object DismissSelectedTranslation : ViewerIntent
    data class SetImageScaleMode(val mode: ImageScaleMode) : ViewerIntent
    data class SetViewMode(val mode: ViewMode) : ViewerIntent
    data class SetReadingDirection(val direction: ReadingDirection) : ViewerIntent
    data class ToggleCachedBlockSelection(val blockIndex: Int) : ViewerIntent
    data class SetAutoProcessMode(val mode: AutoProcessMode) : ViewerIntent
    data object RetryAutoProcess : ViewerIntent
    data class ToggleOcrBlockSelection(val blockIndex: Int) : ViewerIntent
    data class SetBottomTab(val tab: BottomPanelTab) : ViewerIntent
}

sealed interface ViewerSideEffect {
    data object NavigateBack : ViewerSideEffect
}

// ── DOUBLE_PAGE 모드: 스프레드(양면) 인덱스 ↔ 실제 페이지 인덱스 변환 ──

fun spreadCount(totalPages: Int): Int {
    if (totalPages <= 1) return totalPages
    return 1 + (totalPages - 1 + 1) / 2  // 표지(1) + 나머지 짝수 묶음
}

fun spreadToFirstPage(spreadIndex: Int): Int {
    if (spreadIndex == 0) return 0  // 표지
    return spreadIndex * 2 - 1
}

fun spreadToPages(spreadIndex: Int, totalPages: Int): Pair<Int, Int?> {
    if (spreadIndex == 0) return Pair(0, null)  // 표지는 단면
    val first = spreadIndex * 2 - 1
    val second = first + 1
    return Pair(first, if (second < totalPages) second else null)
}

/**
 * ImageScaleMode에 따른 이미지 → 화면 좌표 변환용 스케일/오프셋 계산.
 * @return Pair(scale, Offset(offsetX, offsetY))
 */
fun calculateImageTransform(
    scaleMode: ImageScaleMode,
    containerWidth: Float,
    containerHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
): Pair<Float, Offset> {
    if (imageWidth <= 0f || imageHeight <= 0f) return Pair(1f, Offset.Zero)
    val scale = when (scaleMode) {
        ImageScaleMode.FIT_WIDTH -> containerWidth / imageWidth
        ImageScaleMode.FIT_HEIGHT -> containerHeight / imageHeight
        ImageScaleMode.AUTO_FIT -> minOf(containerWidth / imageWidth, containerHeight / imageHeight)
        ImageScaleMode.ORIGINAL -> 1f
    }
    val offsetX = (containerWidth - imageWidth * scale) / 2f
    val offsetY = (containerHeight - imageHeight * scale) / 2f
    return Pair(scale, Offset(offsetX, offsetY))
}

fun pageToSpread(pageIndex: Int): Int {
    if (pageIndex == 0) return 0
    return (pageIndex + 1) / 2
}
