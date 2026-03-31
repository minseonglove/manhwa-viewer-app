package com.manhwaviewer.presentation.screen.viewer

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.RecognizePageTextUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val getArchiveByIdUseCase: GetArchiveByIdUseCase,
    private val updateLastReadIndexUseCase: UpdateLastReadIndexUseCase,
    getViewerPreferenceUseCase: GetViewerPreferenceUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val getImageBytesUseCase: GetImageBytesUseCase,
    private val recognizePageTextUseCase: RecognizePageTextUseCase,
) : MviViewModel<ViewerState, ViewerIntent, ViewerSideEffect>(ViewerState()) {

    init {
        viewModelScope.launch {
            getViewerPreferenceUseCase().collect { pref ->
                updateState { copy(preference = pref) }
            }
        }
    }

    override fun handleIntent(intent: ViewerIntent) {
        when (intent) {
            is ViewerIntent.LoadArchive -> loadArchive(intent.archiveId, intent.startPage, intent.sortByDate, intent.sortAscending)
            is ViewerIntent.SetPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(currentPage = intent.page.coerceIn(0, totalPages - 1))
                }
            }
            is ViewerIntent.NextPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(currentPage = (currentPage + 1).coerceIn(0, totalPages - 1))
                }
            }
            is ViewerIntent.PreviousPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(currentPage = (currentPage - 1).coerceIn(0, totalPages - 1))
                }
            }
            is ViewerIntent.ToggleControls -> updateState {
                copy(showControls = !showControls)
            }
            is ViewerIntent.SaveProgress -> saveProgress()
            is ViewerIntent.RunPageOcr -> runPageOcr()
            is ViewerIntent.SetTranslationMode -> updateState {
                copy(
                    translationMode = intent.mode,
                    cropRect = if (intent.mode == TranslationMode.NONE) null else cropRect,
                )
            }
            is ViewerIntent.UpdateCropRect -> updateState {
                copy(cropRect = intent.rect)
            }
            is ViewerIntent.ConfirmCrop -> confirmCrop()
            is ViewerIntent.DismissOcrResult -> updateState {
                copy(ocrBottomSheetText = null)
            }
        }
    }

    private fun loadArchive(archiveId: Long, startPage: Int = 0, sortByDate: Boolean = false, sortAscending: Boolean = true) {
        viewModelScope.launch {
            val archive = getArchiveByIdUseCase(archiveId) ?: run {
                updateState { copy(isLoading = false) }
                return@launch
            }
            val entries = getImageEntriesUseCase(archive.filePath, sortByDate, sortAscending)
            val page = if (startPage >= 0) startPage else archive.lastReadIndex
            updateState {
                copy(
                    archive = archive,
                    filePath = archive.filePath,
                    imageEntries = entries,
                    currentPage = page.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
                    totalPages = entries.size,
                    isLoading = false,
                )
            }
        }
    }

    suspend fun getImageBytes(page: Int): ByteArray? {
        val state = currentState
        val entryName = state.imageEntries.getOrNull(page) ?: return null
        return getImageBytesUseCase(state.filePath, entryName)
    }

    private fun saveProgress() {
        viewModelScope.launch {
            val state = currentState
            val archive = state.archive ?: return@launch
            updateLastReadIndexUseCase(archive.id, state.currentPage)
        }
    }

    private fun runPageOcr() {
        if (currentState.isOcrProcessing) return
        viewModelScope.launch {
            updateState {
                copy(
                    isOcrProcessing = true,
                    ocrResults = emptyList(),
                    pipelineStage = PipelineStage.DETECTING,
                )
            }
            try {
                val imageBytes = getImageBytes(currentState.currentPage) ?: run {
                    updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
                    return@launch
                }

                updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }
                val results = recognizePageTextUseCase(imageBytes)

                println("=== OCR Results for page ${currentState.currentPage + 1} ===")
                results.forEachIndexed { index, block ->
                    println("  [$index] (${block.x}, ${block.y}, ${block.width}x${block.height}): ${block.text}")
                }
                println("=== Total: ${results.size} text blocks detected ===")

                val combinedText = results.joinToString("\n") { it.text }

                updateState {
                    copy(
                        isOcrProcessing = false,
                        ocrResults = results,
                        pipelineStage = PipelineStage.DONE,
                        ocrBottomSheetText = combinedText.ifEmpty { null },
                        translationMode = TranslationMode.NONE,
                    )
                }

                // TODO: Stage 5 - 번역 파이프라인 연동
                // TODO: 번역 결과를 오버레이 UI로 표시
                // TODO: 번역 캐시 (Room DB) 저장
                // TODO: Dominant Color 배경 처리
                // TODO: Auto-resizing 텍스트 배치
            } catch (e: Exception) {
                println("OCR Error: ${e.message}")
                updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
            }
        }
    }

    private fun confirmCrop() {
        val crop = currentState.cropRect ?: return
        viewModelScope.launch {
            updateState { copy(pipelineStage = PipelineStage.DETECTING, isOcrProcessing = true) }
            try {
                val imageBytes = getImageBytes(currentState.currentPage) ?: run {
                    updateState {
                        copy(
                            pipelineStage = PipelineStage.IDLE,
                            isOcrProcessing = false,
                            translationMode = TranslationMode.NONE,
                            cropRect = null,
                        )
                    }
                    return@launch
                }

                // 좌표 변환: screen → image 좌표
                // TODO: 실제 이미지 크기와 scale/offset 기반 변환
                // imageRect = (screenRect - offset) / scale

                updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }

                // 크롭 영역의 전체 이미지 OCR 후 해당 영역 내 결과만 필터링
                val results = recognizePageTextUseCase(imageBytes)

                println("=== Crop Area OCR Results ===")
                results.forEachIndexed { index, block ->
                    println("  [$index] (${block.x}, ${block.y}, ${block.width}x${block.height}): ${block.text}")
                }

                // 크롭 영역 내 결과 필터링 (화면 좌표 기준)
                val filteredResults = results.filter { block ->
                    val blockCenterX = block.x + block.width / 2f
                    val blockCenterY = block.y + block.height / 2f
                    val minX = minOf(crop.startX, crop.endX)
                    val maxX = maxOf(crop.startX, crop.endX)
                    val minY = minOf(crop.startY, crop.endY)
                    val maxY = maxOf(crop.startY, crop.endY)
                    blockCenterX in minX..maxX && blockCenterY in minY..maxY
                }

                val combinedText = filteredResults.joinToString("\n") { it.text }

                updateState {
                    copy(
                        pipelineStage = PipelineStage.DONE,
                        isOcrProcessing = false,
                        ocrResults = filteredResults,
                        ocrBottomSheetText = combinedText.ifEmpty { null },
                        translationMode = TranslationMode.NONE,
                        cropRect = null,
                    )
                }

                // TODO: Stage 5 - 번역 파이프라인 연동
            } catch (e: Exception) {
                println("Crop OCR Error: ${e.message}")
                updateState {
                    copy(
                        pipelineStage = PipelineStage.IDLE,
                        isOcrProcessing = false,
                        translationMode = TranslationMode.NONE,
                        cropRect = null,
                    )
                }
            }
        }
    }
}
