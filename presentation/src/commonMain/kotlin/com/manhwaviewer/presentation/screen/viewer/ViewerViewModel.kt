package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetPageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.RecognizePageTextUseCase
import com.manhwaviewer.domain.usecase.SavePageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.TranslateTextUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.domain.usecase.UpdateViewerPreferenceUseCase
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.TranslationBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.entity.PageTranslationCache
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.util.cropImageBytes
import com.manhwaviewer.presentation.util.extractDominantColors
import com.manhwaviewer.presentation.util.getImageDimensions
import com.manhwaviewer.presentation.util.mergeImagesHorizontally
import com.manhwaviewer.domain.entity.ReadingDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private data class PageOcrResult(
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val ocrResults: List<TextBlock>,
    val translationResults: List<TranslationResult>,
    val overlayColors: List<Color>,
)

class ViewerViewModel(
    private val getArchiveByIdUseCase: GetArchiveByIdUseCase,
    private val updateLastReadIndexUseCase: UpdateLastReadIndexUseCase,
    getViewerPreferenceUseCase: GetViewerPreferenceUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
    private val getImageBytesUseCase: GetImageBytesUseCase,
    private val recognizePageTextUseCase: RecognizePageTextUseCase,
    private val translateTextUseCase: TranslateTextUseCase,
    private val updateViewerPreferenceUseCase: UpdateViewerPreferenceUseCase,
    private val savePageTranslationCacheUseCase: SavePageTranslationCacheUseCase,
    private val getPageTranslationCacheUseCase: GetPageTranslationCacheUseCase,
) : MviViewModel<ViewerState, ViewerIntent, ViewerSideEffect>(ViewerState()) {

    private var ocrJob: Job? = null

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
                ocrJob?.cancel()
                updateState {
                    copy(
                        currentPage = newPage,
                        ocrResults = emptyList(),
                        ocrBottomSheetText = null,
                        translationResults = emptyList(),
                        pipelineStage = PipelineStage.IDLE,
                        isOcrSheetExpanded = false,
                        showTranslationOverlay = false,
                        overlayColors = emptyList(),
                        ocrImageWidth = 0,
                        ocrImageHeight = 0,
                    )
                }
            }
            is ViewerIntent.NextPage -> {
                if (currentState.totalPages == 0) return
                ocrJob?.cancel()
                updateState {
                    copy(
                        currentPage = (currentPage + 1).coerceIn(0, totalPages - 1),
                        ocrResults = emptyList(),
                        ocrBottomSheetText = null,
                        translationResults = emptyList(),
                        pipelineStage = PipelineStage.IDLE,
                        isOcrSheetExpanded = false,
                        showTranslationOverlay = false,
                        overlayColors = emptyList(),
                        ocrImageWidth = 0,
                        ocrImageHeight = 0,
                    )
                }
            }
            is ViewerIntent.PreviousPage -> {
                if (currentState.totalPages == 0) return
                ocrJob?.cancel()
                updateState {
                    copy(
                        currentPage = (currentPage - 1).coerceIn(0, totalPages - 1),
                        ocrResults = emptyList(),
                        ocrBottomSheetText = null,
                        translationResults = emptyList(),
                        pipelineStage = PipelineStage.IDLE,
                        isOcrSheetExpanded = false,
                        showTranslationOverlay = false,
                        overlayColors = emptyList(),
                        ocrImageWidth = 0,
                        ocrImageHeight = 0,
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
                    showTranslationOverlay = if (clearOcr) false else showTranslationOverlay,
                    overlayColors = if (clearOcr) emptyList() else overlayColors,
                    ocrImageWidth = if (clearOcr) 0 else ocrImageWidth,
                    ocrImageHeight = if (clearOcr) 0 else ocrImageHeight,
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
            is ViewerIntent.ToggleTranslationOverlay -> updateState {
                copy(showTranslationOverlay = !showTranslationOverlay)
            }
            is ViewerIntent.SetImageScaleMode -> {
                viewModelScope.launch {
                    updateViewerPreferenceUseCase(
                        currentState.preference.copy(imageScaleMode = intent.mode)
                    )
                }
            }
            is ViewerIntent.SetViewMode -> {
                viewModelScope.launch {
                    updateViewerPreferenceUseCase(
                        currentState.preference.copy(viewMode = intent.mode)
                    )
                }
            }
            is ViewerIntent.SetReadingDirection -> {
                viewModelScope.launch {
                    updateViewerPreferenceUseCase(
                        currentState.preference.copy(readingDirection = intent.direction)
                    )
                }
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

    /**
     * 양면 모드의 가상 합성 좌표계 메타데이터.
     * 이미지 바이트를 보유하지 않고, 좌표 계산에 필요한 크기/페이지 정보만 저장.
     */
    private data class VirtualSpreadLayout(
        val totalWidth: Int,
        val totalHeight: Int,
        val leftWidth: Int,
        val leftPage: Int, // 왼쪽 페이지 인덱스 (RTL 적용 후)
        val rightPage: Int?, // 오른쪽 페이지 인덱스
        val leftScale: Float, // leftOrigH / targetH (가상→원본 역변환)
        val rightScale: Float,
    )

    private suspend fun getVirtualSpreadLayout(): VirtualSpreadLayout? {
        val state = currentState
        if (state.preference.viewMode != ViewMode.DOUBLE_PAGE) {
            val bytes = getImageBytes(state.currentPage) ?: return null
            val (w, h) = getImageDimensions(bytes)
            return VirtualSpreadLayout(
                totalWidth = w, totalHeight = h, leftWidth = w,
                leftPage = state.currentPage, rightPage = null,
                leftScale = 1f, rightScale = 1f,
            )
        }
        val spread = pageToSpread(state.currentPage)
        val (firstPage, secondPage) = spreadToPages(spread, state.totalPages)
        val firstBytes = getImageBytes(firstPage) ?: return null
        val secondBytes = if (secondPage != null) getImageBytes(secondPage) else null
        if (secondBytes == null) {
            val (w, h) = getImageDimensions(firstBytes)
            return VirtualSpreadLayout(
                totalWidth = w, totalHeight = h, leftWidth = w,
                leftPage = firstPage, rightPage = null,
                leftScale = 1f, rightScale = 1f,
            )
        }
        val isRtl = state.preference.readingDirection == ReadingDirection.RTL
        val leftPage = if (isRtl) secondPage!! else firstPage
        val rightPage2 = if (isRtl) firstPage else secondPage!!
        val (lw, lh) = getImageDimensions(if (isRtl) secondBytes else firstBytes)
        val (rw, rh) = getImageDimensions(if (isRtl) firstBytes else secondBytes)
        val targetH = maxOf(lh, rh)
        val virtualLeftW = (lw.toFloat() * targetH / lh).toInt()
        return VirtualSpreadLayout(
            totalWidth = virtualLeftW + (rw.toFloat() * targetH / rh).toInt(),
            totalHeight = targetH,
            leftWidth = virtualLeftW,
            leftPage = leftPage, rightPage = rightPage2,
            leftScale = lh.toFloat() / targetH,
            rightScale = rh.toFloat() / targetH,
        )
    }

    /**
     * 가상 합성 좌표계에서 크롭.
     * 필요한 페이지의 바이트만 로드하여 메모리 사용을 최소화.
     */
    private suspend fun cropFromVirtualSpread(
        layout: VirtualSpreadLayout,
        imgLeft: Int, imgTop: Int, cropW: Int, cropH: Int,
    ): ByteArray {
        // 단일 페이지 또는 홀수 마지막 페이지
        if (layout.rightPage == null) {
            val bytes = getImageBytes(layout.leftPage)
                ?: error("Failed to load page ${layout.leftPage}")
            return cropImageBytes(bytes, imgLeft, imgTop, cropW, cropH)
        }

        val imgRight = imgLeft + cropW

        // Case 1: 크롭이 왼쪽 페이지에만 해당 — 왼쪽 바이트만 로드
        if (imgRight <= layout.leftWidth) {
            val bytes = getImageBytes(layout.leftPage)
                ?: error("Failed to load page ${layout.leftPage}")
            val origX = (imgLeft * layout.leftScale).toInt()
            val origY = (imgTop * layout.leftScale).toInt()
            val origW = (cropW * layout.leftScale).toInt().coerceAtLeast(1)
            val origH = (cropH * layout.leftScale).toInt().coerceAtLeast(1)
            return cropImageBytes(bytes, origX, origY, origW, origH)
        }

        // Case 2: 크롭이 오른쪽 페이지에만 해당 — 오른쪽 바이트만 로드
        if (imgLeft >= layout.leftWidth) {
            val bytes = getImageBytes(layout.rightPage)
                ?: error("Failed to load page ${layout.rightPage}")
            val offsetX = imgLeft - layout.leftWidth
            val origX = (offsetX * layout.rightScale).toInt()
            val origY = (imgTop * layout.rightScale).toInt()
            val origW = (cropW * layout.rightScale).toInt().coerceAtLeast(1)
            val origH = (cropH * layout.rightScale).toInt().coerceAtLeast(1)
            return cropImageBytes(bytes, origX, origY, origW, origH)
        }

        // Case 3: 크롭이 양쪽 페이지에 걸침 — 각각 작은 조각만 크롭 후 합성
        val leftCropW = layout.leftWidth - imgLeft
        val rightCropW = cropW - leftCropW

        val leftBytes = getImageBytes(layout.leftPage)
            ?: error("Failed to load page ${layout.leftPage}")
        val leftOrigX = (imgLeft * layout.leftScale).toInt()
        val leftOrigY = (imgTop * layout.leftScale).toInt()
        val leftOrigW = (leftCropW * layout.leftScale).toInt().coerceAtLeast(1)
        val leftOrigH = (cropH * layout.leftScale).toInt().coerceAtLeast(1)
        val leftCrop = cropImageBytes(leftBytes, leftOrigX, leftOrigY, leftOrigW, leftOrigH)

        val rightBytes = getImageBytes(layout.rightPage)
            ?: error("Failed to load page ${layout.rightPage}")
        val rightOrigY = (imgTop * layout.rightScale).toInt()
        val rightOrigW = (rightCropW * layout.rightScale).toInt().coerceAtLeast(1)
        val rightOrigH = (cropH * layout.rightScale).toInt().coerceAtLeast(1)
        val rightCrop = cropImageBytes(rightBytes, 0, rightOrigY, rightOrigW, rightOrigH)

        return mergeImagesHorizontally(leftCrop, rightCrop)
    }

    private fun saveProgress() {
        viewModelScope.launch {
            val state = currentState
            val archive = state.archive ?: return@launch
            updateLastReadIndexUseCase(archive.id, state.currentPage)
        }
    }

    /**
     * 단일 페이지에 대해 OCR + 번역을 수행하고 캐시에 저장한다.
     * 캐시 히트 시 캐시에서 복원한다.
     */
    private suspend fun processPageOcr(pageIndex: Int): PageOcrResult? {
        val state = currentState
        val archive = state.archive ?: return null

        // 캐시 먼저 확인 — 히트 시 이미지 로드는 색상 추출용으로만
        val cached = getPageTranslationCacheUseCase(archive.id, pageIndex)
        if (cached != null) {
            val ocrResults = cached.blocks.map { block ->
                TextBlock(block.x, block.y, block.width, block.height, block.originalText)
            }
            val translationResults = cached.blocks.map { block ->
                TranslationResult(block.originalText, block.translatedText)
            }
            val imageBytes = getImageBytes(pageIndex) ?: return null
            val colors = extractDominantColors(imageBytes, ocrResults)
            return PageOcrResult(
                pageIndex = pageIndex,
                imageWidth = cached.imageWidth,
                imageHeight = cached.imageHeight,
                ocrResults = ocrResults,
                translationResults = translationResults,
                overlayColors = colors,
            )
        }

        // 캐시 미스 — 이미지 로드 후 OCR 실행
        val imageBytes = getImageBytes(pageIndex) ?: return null
        val (imgW, imgH) = getImageDimensions(imageBytes)

        val ocrResults = recognizePageTextUseCase(imageBytes)

        // 빈 OCR 결과도 캐시 저장 — 텍스트 없는 페이지의 불필요한 재실행 방지
        val translationResults = if (ocrResults.isNotEmpty()) {
            translateTextUseCase(ocrResults.map { it.text })
        } else {
            emptyList()
        }
        val colors = if (ocrResults.isNotEmpty()) {
            extractDominantColors(imageBytes, ocrResults)
        } else {
            emptyList()
        }

        // 캐시 저장 (빈 결과 포함)
        val blocks = ocrResults.zip(translationResults).map { (ocr, tr) ->
            TranslationBlock(
                x = ocr.x,
                y = ocr.y,
                width = ocr.width,
                height = ocr.height,
                originalText = tr.originalText,
                translatedText = tr.translatedText,
            )
        }
        savePageTranslationCacheUseCase(
            PageTranslationCache(
                archiveId = archive.id,
                page = pageIndex,
                imageWidth = imgW,
                imageHeight = imgH,
                blocks = blocks,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )

        return PageOcrResult(
            pageIndex = pageIndex,
            imageWidth = imgW,
            imageHeight = imgH,
            ocrResults = ocrResults,
            translationResults = translationResults,
            overlayColors = colors,
        )
    }

    /**
     * 양면 모드에서 두 페이지의 OCR 결과를 합쳐진 이미지의 좌표계로 변환한다.
     *
     * mergeImagesHorizontally는 두 이미지를 targetH = max(h1, h2)로 스케일링한 후 합친다.
     * 따라서 각 페이지의 OCR 좌표도 동일한 스케일링을 적용해야 한다.
     *
     * - LTR: [firstPage | secondPage]
     * - RTL: [secondPage | firstPage]
     */
    private fun combinePageResults(
        firstResult: PageOcrResult,
        secondResult: PageOcrResult?,
        isRtl: Boolean,
    ): PageOcrResult {
        if (secondResult == null) return firstResult

        val leftResult: PageOcrResult
        val rightResult: PageOcrResult
        if (isRtl) {
            leftResult = secondResult
            rightResult = firstResult
        } else {
            leftResult = firstResult
            rightResult = secondResult
        }

        // mergeImagesHorizontally와 동일한 높이 정규화 로직
        val targetH = maxOf(leftResult.imageHeight, rightResult.imageHeight)
        val leftScale = targetH.toFloat() / leftResult.imageHeight
        val rightScale = targetH.toFloat() / rightResult.imageHeight
        val scaledLeftW = (leftResult.imageWidth * leftScale).toInt()
        val scaledRightW = (rightResult.imageWidth * rightScale).toInt()

        val scaledLeftOcr = leftResult.ocrResults.map { block ->
            block.copy(
                x = (block.x * leftScale).toInt(),
                y = (block.y * leftScale).toInt(),
                width = (block.width * leftScale).toInt(),
                height = (block.height * leftScale).toInt(),
            )
        }

        val scaledRightOcr = rightResult.ocrResults.map { block ->
            block.copy(
                x = (block.x * rightScale).toInt() + scaledLeftW,
                y = (block.y * rightScale).toInt(),
                width = (block.width * rightScale).toInt(),
                height = (block.height * rightScale).toInt(),
            )
        }

        return PageOcrResult(
            pageIndex = firstResult.pageIndex,
            imageWidth = scaledLeftW + scaledRightW,
            imageHeight = targetH,
            ocrResults = scaledLeftOcr + scaledRightOcr,
            translationResults = leftResult.translationResults + rightResult.translationResults,
            overlayColors = leftResult.overlayColors + rightResult.overlayColors,
        )
    }

    private fun runPageOcr() {
        if (currentState.isOcrProcessing) return
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            updateState {
                copy(
                    isOcrProcessing = true,
                    ocrResults = emptyList(),
                    pipelineStage = PipelineStage.DETECTING,
                    translationMode = TranslationMode.PAGE_TRANSLATE,
                )
            }
            try {
                val state = currentState
                val isDoublePage = state.preference.viewMode == ViewMode.DOUBLE_PAGE

                val combined: PageOcrResult?

                if (isDoublePage) {
                    val spread = pageToSpread(state.currentPage)
                    val (firstPage, secondPage) = spreadToPages(spread, state.totalPages)

                    updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }

                    // 각 페이지를 병렬로 OCR + 번역
                    val firstDeferred = async { processPageOcr(firstPage) }
                    val secondDeferred = if (secondPage != null) async { processPageOcr(secondPage) } else null

                    val firstResult = firstDeferred.await()
                    val secondResult = secondDeferred?.await()

                    if (firstResult == null) {
                        updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
                        return@launch
                    }

                    val isRtl = state.preference.readingDirection == ReadingDirection.RTL
                    combined = combinePageResults(firstResult, secondResult, isRtl)
                } else {
                    // 단면/웹툰 모드: 단일 페이지 처리
                    updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }
                    combined = processPageOcr(state.currentPage)

                    if (combined == null) {
                        updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
                        return@launch
                    }
                }

                val combinedText = combined.ocrResults.joinToString("\n") { it.text }

                updateState {
                    copy(
                        isOcrProcessing = false,
                        ocrResults = combined.ocrResults,
                        translationResults = combined.translationResults,
                        pipelineStage = PipelineStage.DONE,
                        ocrBottomSheetText = combinedText.ifEmpty { null },
                        isOcrSheetExpanded = combinedText.isNotEmpty(),
                        ocrImageWidth = combined.imageWidth,
                        ocrImageHeight = combined.imageHeight,
                        isTranslating = false,
                        showTranslationOverlay = combined.translationResults.isNotEmpty(),
                        overlayColors = combined.overlayColors,
                    )
                }
            } catch (e: Exception) {
                updateState { copy(isOcrProcessing = false, pipelineStage = PipelineStage.IDLE) }
            }
        }
    }

    private fun confirmCrop(containerWidth: Int, containerHeight: Int) {
        val crop = currentState.cropRect ?: return
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            updateState { copy(pipelineStage = PipelineStage.DETECTING, isOcrProcessing = true) }
            try {
                // 가상 합성 좌표계 크기 계산 (실제 합성 없이)
                val virtualLayout = getVirtualSpreadLayout() ?: run {
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
                val imgW = virtualLayout.totalWidth
                val imgH = virtualLayout.totalHeight

                // ImageScaleMode에 따른 좌표 변환: screen → image 좌표
                val (fitScale, transformOffset) = calculateImageTransform(
                    currentState.preference.imageScaleMode,
                    containerWidth.toFloat(),
                    containerHeight.toFloat(),
                    imgW.toFloat(),
                    imgH.toFloat(),
                )
                val offsetX = transformOffset.x
                val offsetY = transformOffset.y

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

                // 이미지 크롭 (양면 모드: 페이지별 분리 크롭, 단일 모드: 직접 크롭)
                val croppedBytes = cropFromVirtualSpread(virtualLayout, imgLeft, imgTop, cropW, cropH)

                updateState { copy(pipelineStage = PipelineStage.RECOGNIZING) }

                // 크롭된 이미지로 OCR 실행
                val results = recognizePageTextUseCase(croppedBytes)
                val (cropImgW, cropImgH) = getImageDimensions(croppedBytes)

                val combinedText = results.joinToString("\n") { it.text }

                updateState {
                    copy(
                        pipelineStage = PipelineStage.TRANSLATING,
                        isOcrProcessing = false,
                        ocrResults = results,
                        ocrBottomSheetText = combinedText.ifEmpty { null },
                        isOcrSheetExpanded = combinedText.isNotEmpty(),
                        ocrImageWidth = cropImgW,
                        ocrImageHeight = cropImgH,
                    )
                }

                if (results.isNotEmpty()) {
                    updateState { copy(isTranslating = true) }
                    try {
                        val translationResults = translateTextUseCase(results.map { it.text })
                        val colors = extractDominantColors(croppedBytes, results)
                        updateState {
                            copy(
                                translationResults = translationResults,
                                pipelineStage = PipelineStage.DONE,
                                isTranslating = false,
                                showTranslationOverlay = translationResults.isNotEmpty(),
                                overlayColors = colors,
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
