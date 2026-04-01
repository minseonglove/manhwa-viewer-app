package com.manhwaviewer.presentation.screen.viewer

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.RecognizePageTextUseCase
import com.manhwaviewer.domain.usecase.TranslateTextUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.util.cropImageBytes
import com.manhwaviewer.presentation.util.getImageDimensions
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val getArchiveByIdUseCase: GetArchiveByIdUseCase,
    private val updateLastReadIndexUseCase: UpdateLastReadIndexUseCase,
    getViewerPreferenceUseCase: GetViewerPreferenceUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val getImageBytesUseCase: GetImageBytesUseCase,
    private val recognizePageTextUseCase: RecognizePageTextUseCase,
    private val translateTextUseCase: TranslateTextUseCase,
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
                val newPage = intent.page.coerceIn(0, currentState.totalPages - 1)
                if (newPage == currentState.currentPage) return
                updateState {
                    copy(
                        currentPage = newPage,
                        ocrResults = emptyList(),
                        ocrBottomSheetText = null,
                        translationResults = emptyList(),
                        pipelineStage = PipelineStage.IDLE,
                        isOcrSheetExpanded = false,
                    )
                }
            }
            is ViewerIntent.NextPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(
                        currentPage = (currentPage + 1).coerceIn(0, totalPages - 1),
                        ocrResults = emptyList(),
                        ocrBottomSheetText = null,
                        translationResults = emptyList(),
                        pipelineStage = PipelineStage.IDLE,
                        isOcrSheetExpanded = false,
                    )
                }
            }
            is ViewerIntent.PreviousPage -> {
                if (currentState.totalPages == 0) return
                updateState {
                    copy(
                        currentPage = (currentPage - 1).coerceIn(0, totalPages - 1),
                        ocrResults = emptyList(),
                        ocrBottomSheetText = null,
                        translationResults = emptyList(),
                        pipelineStage = PipelineStage.IDLE,
                        isOcrSheetExpanded = false,
                    )
                }
            }
            is ViewerIntent.ToggleControls -> updateState {
                copy(showControls = !showControls)
            }
            is ViewerIntent.SaveProgress -> saveProgress()
            is ViewerIntent.RunPageOcr -> runPageOcr()
            is ViewerIntent.SetTranslationMode -> updateState {
                val clearOcr = intent.mode == TranslationMode.NONE || intent.mode == TranslationMode.AREA_SELECT
                copy(
                    translationMode = intent.mode,
                    cropRect = when (intent.mode) {
                        TranslationMode.AREA_SELECT -> CropRect(
                            startX = -1f, startY = -1f,
                            endX = -1f, endY = -1f,
                        )
                        TranslationMode.NONE -> null
                        else -> cropRect
                    },
                    ocrBottomSheetText = if (clearOcr) null else ocrBottomSheetText,
                    ocrResults = if (clearOcr) emptyList() else ocrResults,
                    isOcrSheetExpanded = if (clearOcr) false else isOcrSheetExpanded,
                    pipelineStage = if (clearOcr) PipelineStage.IDLE else pipelineStage,
                    translationResults = if (clearOcr) emptyList() else translationResults,
                    isTranslating = if (clearOcr) false else isTranslating,
                )
            }
            is ViewerIntent.UpdateCropRect -> updateState {
                copy(cropRect = intent.rect)
            }
            is ViewerIntent.ConfirmCrop -> confirmCrop(intent.containerWidth, intent.containerHeight)
            is ViewerIntent.ExpandOcrResult -> updateState {
                copy(isOcrSheetExpanded = true)
            }
            is ViewerIntent.DismissOcrResult -> updateState {
                copy(isOcrSheetExpanded = false)
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
                    translationMode = TranslationMode.PAGE_TRANSLATE,
                )
            }
            try {
                val imageBytes = getImageBytes(currentState.currentPage) ?: run {
                    updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
                    return@launch
                }

                updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }
                val results = recognizePageTextUseCase(imageBytes)

                val combinedText = results.joinToString("\n") { it.text }

                updateState {
                    copy(
                        isOcrProcessing = false,
                        ocrResults = results,
                        pipelineStage = PipelineStage.TRANSLATING,
                        ocrBottomSheetText = combinedText.ifEmpty { null },
                        isOcrSheetExpanded = combinedText.isNotEmpty(),
                    )
                }

                if (results.isNotEmpty()) {
                    updateState { copy(isTranslating = true) }
                    try {
                        val translationResults = translateTextUseCase(results.map { it.text })
                        updateState {
                            copy(
                                translationResults = translationResults,
                                pipelineStage = PipelineStage.DONE,
                                isTranslating = false,
                            )
                        }
                    } catch (e: Exception) {
                        updateState {
                            copy(
                                translationResults = emptyList(),
                                pipelineStage = PipelineStage.DONE,
                                isTranslating = false,
                            )
                        }
                    }
                } else {
                    updateState { copy(pipelineStage = PipelineStage.DONE) }
                }

                // TODO: 번역 결과를 오버레이 UI로 표시
                // TODO: Dominant Color 배경 처리
                // TODO: Auto-resizing 텍스트 배치
            } catch (e: Exception) {
                updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
            }
        }
    }

    private fun confirmCrop(containerWidth: Int, containerHeight: Int) {
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

                // 이미지 원본 크기 조회
                val (imgW, imgH) = getImageDimensions(imageBytes)

                // ContentScale.Fit 좌표 변환: screen → image 좌표
                val fitScale = minOf(
                    containerWidth.toFloat() / imgW,
                    containerHeight.toFloat() / imgH,
                )
                val offsetX = (containerWidth - imgW * fitScale) / 2f
                val offsetY = (containerHeight - imgH * fitScale) / 2f

                val cropMinX = minOf(crop.startX, crop.endX)
                val cropMinY = minOf(crop.startY, crop.endY)
                val cropMaxX = maxOf(crop.startX, crop.endX)
                val cropMaxY = maxOf(crop.startY, crop.endY)

                val imgLeft = ((cropMinX - offsetX) / fitScale).toInt().coerceIn(0, imgW)
                val imgTop = ((cropMinY - offsetY) / fitScale).toInt().coerceIn(0, imgH)
                val imgRight = ((cropMaxX - offsetX) / fitScale).toInt().coerceIn(0, imgW)
                val imgBottom = ((cropMaxY - offsetY) / fitScale).toInt().coerceIn(0, imgH)
                val cropW = (imgRight - imgLeft).coerceAtLeast(1)
                val cropH = (imgBottom - imgTop).coerceAtLeast(1)

                // 이미지 크롭
                val croppedBytes = cropImageBytes(imageBytes, imgLeft, imgTop, cropW, cropH)

                updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }

                // 크롭된 이미지로 OCR 실행
                val results = recognizePageTextUseCase(croppedBytes)

                val combinedText = results.joinToString("\n") { it.text }

                updateState {
                    copy(
                        pipelineStage = PipelineStage.TRANSLATING,
                        isOcrProcessing = false,
                        ocrResults = results,
                        ocrBottomSheetText = combinedText.ifEmpty { null },
                        isOcrSheetExpanded = combinedText.isNotEmpty(),
                    )
                }

                if (results.isNotEmpty()) {
                    updateState { copy(isTranslating = true) }
                    try {
                        val translationResults = translateTextUseCase(results.map { it.text })
                        updateState {
                            copy(
                                translationResults = translationResults,
                                pipelineStage = PipelineStage.DONE,
                                isTranslating = false,
                            )
                        }
                    } catch (e: Exception) {
                        updateState {
                            copy(
                                translationResults = emptyList(),
                                isTranslating = false,
                                pipelineStage = PipelineStage.DONE,
                            )
                        }
                    }
                } else {
                    updateState { copy(pipelineStage = PipelineStage.DONE) }
                }
            } catch (e: Exception) {
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
