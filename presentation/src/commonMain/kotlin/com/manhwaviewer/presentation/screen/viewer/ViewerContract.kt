package com.manhwaviewer.presentation.screen.viewer

import com.manhwaviewer.domain.entity.ArchiveFile
import androidx.compose.ui.graphics.Color
import com.manhwaviewer.domain.entity.OcrBlock
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationBlock
import com.manhwaviewer.domain.entity.TranslationResult
import androidx.compose.ui.geometry.Offset
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.domain.entity.ViewerPreference

enum class TranslationMode {
    NONE, AREA_SELECT, PAGE_TRANSLATE
}

enum class PipelineStage {
    IDLE, DETECTING, CROPPING, RECOGNIZING, TRANSLATING, DONE
}

enum class AutoOcrStatus {
    IDLE, PROCESSING, DONE
}

data class CropRect(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f,
)

data class ViewerState(
    val archive: ArchiveFile? = null,
    val filePath: String = "",
    val imageEntries: List<String> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val preference: ViewerPreference = ViewerPreference(),
    val showControls: Boolean = false,
    val isLoading: Boolean = true,
    val isOcrProcessing: Boolean = false,
    val ocrResults: List<TextBlock> = emptyList(),
    val translationMode: TranslationMode = TranslationMode.NONE,
    val cropRect: CropRect? = null,
    val pipelineStage: PipelineStage = PipelineStage.IDLE,
    val ocrBottomSheetText: String? = null,
    val isOcrSheetExpanded: Boolean = false,
    val translationResults: List<TranslationResult> = emptyList(),
    val isTranslating: Boolean = false,
    val showTranslationOverlay: Boolean = false,
    val overlayColors: List<Color> = emptyList(),
    val ocrImageWidth: Int = 0,
    val ocrImageHeight: Int = 0,
    val cachedBlocks: List<TranslationBlock> = emptyList(),
    val cachedImageWidth: Int = 0,
    val cachedImageHeight: Int = 0,
    val cachedOverlayColors: List<Color> = emptyList(),
    val visibleCachedBlockIndices: Set<Int> = emptySet(),
    val autoOcrEnabled: Boolean = false,
    val autoOcrStatus: AutoOcrStatus = AutoOcrStatus.IDLE,
    val autoOcrBlocks: List<OcrBlock> = emptyList(),
    val autoOcrImageWidth: Int = 0,
    val autoOcrImageHeight: Int = 0,
    val selectedOcrBlockIndices: Set<Int> = emptySet(),
)

sealed interface ViewerIntent {
    data class LoadArchive(val archiveId: Long, val startPage: Int = -1, val sortByDate: Boolean = false, val sortAscending: Boolean = true) : ViewerIntent
    data class SetPage(val page: Int) : ViewerIntent
    data object NextPage : ViewerIntent
    data object PreviousPage : ViewerIntent
    data object ToggleControls : ViewerIntent
    data object SaveProgress : ViewerIntent
    data object RunPageOcr : ViewerIntent
    data class SetTranslationMode(val mode: TranslationMode) : ViewerIntent
    data class UpdateCropRect(val rect: CropRect) : ViewerIntent
    data class ConfirmCrop(
        val containerWidth: Int,
        val containerHeight: Int,
        val zoomScaleX: Float = 1f,
        val zoomScaleY: Float = 1f,
        val zoomOffsetX: Float = 0f,
        val zoomOffsetY: Float = 0f,
        val transformOriginX: Float = 0.5f,
        val transformOriginY: Float = 0.5f,
        val useSubSampling: Boolean = false,
    ) : ViewerIntent
    data object ExpandOcrResult : ViewerIntent
    data object DismissOcrResult : ViewerIntent
    data object ToggleTranslationOverlay : ViewerIntent
    data class SetImageScaleMode(val mode: ImageScaleMode) : ViewerIntent
    data class SetViewMode(val mode: ViewMode) : ViewerIntent
    data class SetReadingDirection(val direction: ReadingDirection) : ViewerIntent
    data class ToggleCachedBlockOverlay(val blockIndex: Int) : ViewerIntent
    data object ToggleAutoOcr : ViewerIntent
    data class ToggleOcrBlockSelection(val blockIndex: Int) : ViewerIntent
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
