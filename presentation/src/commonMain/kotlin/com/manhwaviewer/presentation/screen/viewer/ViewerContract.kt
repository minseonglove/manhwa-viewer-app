package com.manhwaviewer.presentation.screen.viewer

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.TextBlock
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
)

sealed interface ViewerIntent {
    data class LoadArchive(val archiveId: Long, val startPage: Int = 0) : ViewerIntent
    data class SetPage(val page: Int) : ViewerIntent
    data object NextPage : ViewerIntent
    data object PreviousPage : ViewerIntent
    data object ToggleControls : ViewerIntent
    data object SaveProgress : ViewerIntent
    data object RunPageOcr : ViewerIntent
    data class SetTranslationMode(val mode: TranslationMode) : ViewerIntent
    data class UpdateCropRect(val rect: CropRect) : ViewerIntent
    data object ConfirmCrop : ViewerIntent
    data object DismissOcrResult : ViewerIntent
}

sealed interface ViewerSideEffect {
    data object NavigateBack : ViewerSideEffect
}
