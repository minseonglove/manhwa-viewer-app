package com.manhwaviewer.presentation.screen.viewer

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.entity.ViewerPreference

enum class TranslationMode {
    NONE, AREA_SELECT, PAGE_TRANSLATE
}

enum class PipelineStage {
    IDLE, DETECTING, CROPPING, RECOGNIZING, TRANSLATING, DONE
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
    data class ConfirmCrop(val containerWidth: Int, val containerHeight: Int) : ViewerIntent
    data object ExpandOcrResult : ViewerIntent
    data object DismissOcrResult : ViewerIntent
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

fun pageToSpread(pageIndex: Int): Int {
    if (pageIndex == 0) return 0
    return (pageIndex + 1) / 2
}
