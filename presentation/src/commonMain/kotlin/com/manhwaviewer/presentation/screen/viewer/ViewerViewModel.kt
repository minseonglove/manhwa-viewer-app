package com.manhwaviewer.presentation.screen.viewer

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.usecase.GetArchiveByIdUseCase
import com.manhwaviewer.domain.usecase.GetImageBytesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.GetPageOcrCacheUseCase
import com.manhwaviewer.domain.usecase.GetPageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.GetViewerPreferenceUseCase
import com.manhwaviewer.domain.usecase.RecognizePageTextUseCase
import com.manhwaviewer.domain.usecase.SavePageOcrCacheUseCase
import com.manhwaviewer.domain.usecase.SavePageTranslationCacheUseCase
import com.manhwaviewer.domain.usecase.TranslateTextUseCase
import com.manhwaviewer.domain.usecase.UpdateLastReadIndexUseCase
import com.manhwaviewer.domain.usecase.GetNextArchiveUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.domain.usecase.UpdateViewerPreferenceUseCase
import com.manhwaviewer.domain.entity.TextBlock
import com.manhwaviewer.domain.entity.OcrBlock
import com.manhwaviewer.domain.entity.PageOcrCache
import com.manhwaviewer.domain.entity.TranslationBlock
import com.manhwaviewer.domain.entity.TranslationResult
import com.manhwaviewer.domain.entity.PageTranslationCache
import com.manhwaviewer.domain.entity.ViewMode
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.util.cropImageBytes
import com.manhwaviewer.presentation.util.extractDominantColors
import com.manhwaviewer.presentation.util.getImageDimensions
import com.manhwaviewer.presentation.util.mergeImagesHorizontally
import com.manhwaviewer.domain.entity.ImageScaleMode
import com.manhwaviewer.domain.entity.ReadingDirection
import com.manhwaviewer.domain.repository.DetectionStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock


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
    private val getPageOcrCacheUseCase: GetPageOcrCacheUseCase,
    private val savePageOcrCacheUseCase: SavePageOcrCacheUseCase,
    private val getNextArchiveUseCase: GetNextArchiveUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
) : MviViewModel<ViewerState, ViewerIntent, ViewerSideEffect>(ViewerState()) {

    private var cacheJob: Job? = null
    private var autoOcrJob: Job? = null
    private var autoTranslateJob: Job? = null
    private var pageChangeDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            getViewerPreferenceUseCase().collect { pref ->
                val oldMode = currentState.preference.viewMode
                updateState { copy(preference = pref) }
                // viewMode가 변경되면 좌표계가 달라지므로 오버레이 상태 초기화 + 캐시 리로드
                if (pref.viewMode != oldMode && currentState.totalPages > 0) {
                    cancelAllAutoJobs()
                    updateState { resetPageOcrState() }
                    loadCachedTranslation(currentState.currentPage)
                    if (currentState.autoProcessMode != AutoProcessMode.NONE) {
                        scheduleAutoProcess(currentState.currentPage)
                    }
                }
            }
        }
    }

    override fun handleIntent(intent: ViewerIntent) {
        when (intent) {
            is ViewerIntent.LoadArchive -> loadArchive(intent.archiveId, intent.startPage, intent.sortByDate, intent.sortAscending)
            is ViewerIntent.SetPage -> {
                if (currentState.totalPages == 0) return
                if (intent.page >= currentState.totalPages && currentState.nextVolume != null) {
                    navigateToNextVolume()
                    return
                }
                val newPage = intent.page.coerceIn(0, currentState.totalPages - 1)
                if (newPage == currentState.currentPage) return
                handlePageChange(newPage)
            }
            is ViewerIntent.NextPage -> {
                if (currentState.totalPages == 0) return
                if (currentState.currentPage >= currentState.totalPages - 1 && currentState.nextVolume != null) {
                    navigateToNextVolume()
                    return
                }
                val newPage = (currentState.currentPage + 1).coerceIn(0, currentState.totalPages - 1)
                handlePageChange(newPage)
            }
            is ViewerIntent.PreviousPage -> {
                if (currentState.totalPages == 0) return
                val newPage = (currentState.currentPage - 1).coerceIn(0, currentState.totalPages - 1)
                handlePageChange(newPage)
            }
            is ViewerIntent.ToggleControls -> updateState {
                copy(showControls = !showControls)
            }
            is ViewerIntent.SaveProgress -> saveProgress()
            is ViewerIntent.ToggleGlobalOverlay -> {
                val newValue = !currentState.globalOverlayEnabled
                updateState { copy(globalOverlayEnabled = newValue) }
                if (newValue && currentState.autoProcessMode != AutoProcessMode.AUTO_TRANSLATE
                    && currentState.autoProcessStatus == AutoProcessStatus.OCR_DONE
                    && currentState.autoTranslationBlocks.isEmpty()
                    && autoTranslateJob?.isActive != true) {
                    triggerOverlayTranslation()
                }
            }
            is ViewerIntent.TranslateSelectedBlocks -> translateSelectedBlocks()
            is ViewerIntent.DismissSelectedTranslation -> updateState {
                copy(isSelectedTranslationSheetVisible = false, selectedBlockTranslations = emptyList())
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
            is ViewerIntent.ToggleCachedBlockSelection -> {
                val idx = intent.blockIndex
                val newSet = if (idx in currentState.selectedCachedBlockIndices)
                    currentState.selectedCachedBlockIndices - idx else currentState.selectedCachedBlockIndices + idx
                updateState {
                    copy(
                        selectedCachedBlockIndices = newSet,
                        showControls = if (newSet.isNotEmpty()) true else showControls,
                    )
                }
            }
            is ViewerIntent.SetAutoProcessMode -> handleSetAutoProcessMode(intent.mode)
            is ViewerIntent.RetryAutoProcess -> handleRetryAutoProcess()
            is ViewerIntent.SetBottomTab -> updateState { copy(selectedBottomTab = intent.tab) }
            is ViewerIntent.NavigateToNextVolume -> navigateToNextVolume()
            is ViewerIntent.ToggleOcrBlockSelection -> {
                val idx = intent.blockIndex
                val newSet = if (idx in currentState.selectedOcrBlockIndices)
                    currentState.selectedOcrBlockIndices - idx else currentState.selectedOcrBlockIndices + idx
                updateState {
                    copy(
                        selectedOcrBlockIndices = newSet,
                        showControls = if (newSet.isNotEmpty()) true else showControls,
                    )
                }
            }
        }
    }

    // ── 선택 번역 ─────────────────────────────────────────────────

    private var selectedTranslateJob: Job? = null

    private fun translateSelectedBlocks() {
        val state = currentState

        // 캐시 블록 선택: 이미 번역이 있으므로 API 호출 불필요
        val cachedPairs = state.selectedCachedBlockIndices.sorted().mapNotNull { i ->
            state.cachedBlocks.getOrNull(i)?.let { it.originalText to it.translatedText }
        }

        // OCR 블록 선택: API 호출 필요
        val ocrTexts = state.selectedOcrBlockIndices.sorted().mapNotNull { i ->
            state.autoOcrBlocks.getOrNull(i)?.text
        }

        if (cachedPairs.isEmpty() && ocrTexts.isEmpty()) return

        // 캐시만 있으면 즉시 표시
        if (ocrTexts.isEmpty()) {
            updateState {
                copy(
                    selectedBlockTranslations = cachedPairs,
                    isSelectedTranslating = false,
                    isSelectedTranslationSheetVisible = true,
                )
            }
            return
        }

        // OCR 블록이 있으면 API 호출 (+ 캐시 결과 합산)
        selectedTranslateJob?.cancel()
        selectedTranslateJob = viewModelScope.launch {
            updateState { copy(isSelectedTranslating = true, isSelectedTranslationSheetVisible = true) }
            try {
                val results = translateTextUseCase(ocrTexts)
                val ocrPairs = ocrTexts.zip(results.map { it.translatedText })
                updateState {
                    copy(
                        selectedBlockTranslations = cachedPairs + ocrPairs,
                        isSelectedTranslating = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateState {
                    copy(
                        isSelectedTranslating = false,
                        isSelectedTranslationSheetVisible = false,
                    )
                }
            }
        }
    }

    // ── 오버레이 ON 시 번역 트리거 ──────────────────────────────────

    private fun triggerOverlayTranslation() {
        val state = currentState
        val page = state.currentPage
        val ocrData = PageOcrCache(
            archiveId = state.archive?.id ?: return,
            page = page,
            imageWidth = state.autoOcrImageWidth,
            imageHeight = state.autoOcrImageHeight,
            blocks = state.autoOcrBlocks,
            createdAt = 0L,
        )
        runAutoTranslate(page, ocrData)
    }

    private fun loadArchive(archiveId: Long, startPage: Int = 0, sortByDate: Boolean = false, sortAscending: Boolean = true) {
        viewModelScope.launch {
            val archive = getArchiveByIdUseCase(archiveId) ?: run {
                updateState { copy(isLoading = false) }
                return@launch
            }
            val entries = getImageEntriesUseCase(archive.filePath, sortByDate, sortAscending)
            val page = if (startPage >= 0) startPage else archive.lastReadIndex
            val initialPage = page.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
            updateState {
                copy(
                    archive = archive,
                    filePath = archive.filePath,
                    imageEntries = entries,
                    currentPage = initialPage,
                    totalPages = entries.size,
                    isLoading = false,
                )
            }
            if (currentState.autoProcessMode == AutoProcessMode.NONE) {
                loadCachesForNoneMode(initialPage)
            } else {
                loadCachedTranslation(initialPage)
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
     * 캐시된 블록에서 색상을 복원한다. 색상이 저장되어 있지 않은 레거시 캐시의 경우
     * 이미지 바이트를 로드하여 추출한다.
     */
    private suspend fun restoreOrExtractColors(
        blocks: List<TranslationBlock>,
        textBlocks: List<TextBlock>,
        pageIndex: Int,
    ): List<Color>? {
        val hasColors = blocks.all { it.dominantColorArgb != null }
        return if (hasColors) {
            blocks.map { Color(it.dominantColorArgb!!) }
        } else {
            val imageBytes = getImageBytes(pageIndex) ?: return null
            extractDominantColors(imageBytes, textBlocks)
        }
    }

    /**
     * 페이지의 번역 캐시를 로드하여 개별 말풍선 탭 오버레이용 상태에 저장한다.
     * 양면 모드에서는 스프레드의 두 페이지를 합성 좌표계로 변환한다.
     */
    private fun loadCachedTranslation(page: Int) {
        val archive = currentState.archive ?: return
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            try {
                val isDoublePage = currentState.preference.viewMode == ViewMode.DOUBLE_PAGE

                if (isDoublePage) {
                    val spread = pageToSpread(page)
                    val (firstPage, secondPage) = spreadToPages(spread, currentState.totalPages)
                    val isRtl = currentState.preference.readingDirection == ReadingDirection.RTL

                    val firstCache = getPageTranslationCacheUseCase(archive.id, firstPage)
                    val secondCache = if (secondPage != null) getPageTranslationCacheUseCase(archive.id, secondPage) else null

                    if (firstCache == null && secondCache == null) { clearCachedBlocks(); return@launch }
                    if ((firstCache == null || firstCache.blocks.isEmpty()) &&
                        (secondCache == null || secondCache.blocks.isEmpty())) { clearCachedBlocks(); return@launch }

                    // 캐시 → PageOcrResult 변환 후 합성
                    val firstResult = firstCache?.takeIf { it.blocks.isNotEmpty() }?.let { cache ->
                        val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
                        val colors = restoreOrExtractColors(cache.blocks, textBlocks, firstPage)
                            ?: return@let null
                        PageOcrResult(
                            firstPage, cache.imageWidth, cache.imageHeight, textBlocks,
                            cache.blocks.map { TranslationResult(it.originalText, it.translatedText) }, colors,
                        )
                    }
                    val secondResult = secondCache?.takeIf { it.blocks.isNotEmpty() }?.let { cache ->
                        val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
                        val colors = restoreOrExtractColors(cache.blocks, textBlocks, secondPage!!)
                            ?: return@let null
                        PageOcrResult(
                            secondPage, cache.imageWidth, cache.imageHeight, textBlocks,
                            cache.blocks.map { TranslationResult(it.originalText, it.translatedText) }, colors,
                        )
                    }

                    if (firstResult == null && secondResult == null) { clearCachedBlocks(); return@launch }

                    // 한쪽만 번역이 있어도 스프레드 좌표계로 변환해야 함
                    val combined = combinePageResults(
                        firstResult ?: emptyPageOcrResult(firstPage),
                        secondResult ?: (secondPage?.let { emptyPageOcrResult(it) }),
                        isRtl,
                    )

                    val cachedTranslationBlocks = combined.ocrResults.zip(combined.translationResults).map { (ocr, tr) ->
                        TranslationBlock(ocr.x, ocr.y, ocr.width, ocr.height, tr.originalText, tr.translatedText)
                    }

                    updateState {
                        copy(
                            cachedBlocks = cachedTranslationBlocks,
                            cachedImageWidth = combined.imageWidth,
                            cachedImageHeight = combined.imageHeight,
                            cachedOverlayColors = combined.overlayColors,
                            selectedCachedBlockIndices = emptySet(),
                        )
                    }
                } else {
                    val cache = getPageTranslationCacheUseCase(archive.id, page)
                    if (cache == null || cache.blocks.isEmpty()) { clearCachedBlocks(); return@launch }

                    val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
                    val colors = restoreOrExtractColors(cache.blocks, textBlocks, page) ?: return@launch

                    updateState {
                        copy(
                            cachedBlocks = cache.blocks,
                            cachedImageWidth = cache.imageWidth,
                            cachedImageHeight = cache.imageHeight,
                            cachedOverlayColors = colors,
                            selectedCachedBlockIndices = emptySet(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                clearCachedBlocks()
            }
        }
    }

    // ── 자동 OCR ──────────────────────────────────────────────────

    /**
     * 단일 페이지에 대해 OCR만 실행하고 캐시에 저장한다.
     * OCR 캐시 또는 번역 캐시가 있으면 즉시 반환한다.
     */
    private suspend fun processPageOcrOnly(pageIndex: Int): PageOcrCache? {
        val archive = currentState.archive ?: return null

        // OCR 캐시 확인
        val ocrCache = getPageOcrCacheUseCase(archive.id, pageIndex)
        if (ocrCache != null) return ocrCache

        // 번역 캐시에 OCR 결과가 있는 경우도 확인
        val translationCache = getPageTranslationCacheUseCase(archive.id, pageIndex)
        if (translationCache != null) {
            return PageOcrCache(
                id = translationCache.id,
                archiveId = translationCache.archiveId,
                page = translationCache.page,
                imageWidth = translationCache.imageWidth,
                imageHeight = translationCache.imageHeight,
                blocks = translationCache.blocks.map {
                    OcrBlock(it.x, it.y, it.width, it.height, it.originalText, it.dominantColorArgb)
                },
                createdAt = translationCache.createdAt,
            )
        }

        // 캐시 미스 — OCR 실행
        val imageBytes = getImageBytes(pageIndex) ?: return null
        val (imgW, imgH) = getImageDimensions(imageBytes)
        val ocrResults = recognizePageTextUseCase(imageBytes) { stage ->
            val pipelineStage = when (stage) {
                DetectionStage.DETECTING -> PipelineStage.DETECTING
                DetectionStage.CROPPING -> PipelineStage.CROPPING
                DetectionStage.RECOGNIZING -> PipelineStage.RECOGNIZING
            }
            updateState { copy(pipelineStage = pipelineStage) }
        }

        val ocrBlocks = ocrResults.map { block ->
            OcrBlock(block.x, block.y, block.width, block.height, block.text)
        }

        // OCR 캐시 저장
        val savedId = savePageOcrCacheUseCase(
            PageOcrCache(
                archiveId = archive.id,
                page = pageIndex,
                imageWidth = imgW,
                imageHeight = imgH,
                blocks = ocrBlocks,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )

        return PageOcrCache(
            id = savedId,
            archiveId = archive.id,
            page = pageIndex,
            imageWidth = imgW,
            imageHeight = imgH,
            blocks = ocrBlocks,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    // ── 페이지 변경 공통 처리 ──────────────────────────────────────

    private fun handlePageChange(newPage: Int) {
        cancelAllAutoJobs()
        val totalPages = currentState.totalPages
        val isLast = if (currentState.preference.viewMode == ViewMode.DOUBLE_PAGE && totalPages > 0) {
            val spread = pageToSpread(newPage)
            val pages = spreadToPages(spread, totalPages)
            val lastPageInSpread = pages.second ?: pages.first
            lastPageInSpread >= totalPages - 1
        } else {
            newPage >= totalPages - 1
        }
        updateState { copy(currentPage = newPage, isLastPage = isLast).resetPageOcrStateKeepingCache() }
        if (currentState.autoProcessMode != AutoProcessMode.NONE) {
            loadCachedTranslation(newPage)
            scheduleAutoProcess(newPage)
        } else {
            // 번역 안함 모드: 번역+OCR 캐시를 한 번에 조회 후 원자적 갱신
            loadCachesForNoneMode(newPage)
        }
        if (isLast && currentState.nextVolume == null && nextVolumeJob?.isActive != true) {
            loadNextVolume()
        }
    }

    private var nextVolumeJob: Job? = null

    private fun loadNextVolume() {
        nextVolumeJob?.cancel()
        nextVolumeJob = viewModelScope.launch {
            val filePath = currentState.filePath.ifEmpty { return@launch }
            val nextArchive = getNextArchiveUseCase(filePath) ?: return@launch
            val archiveId = upsertArchiveUseCase(nextArchive)
            val entries = getImageEntriesUseCase(nextArchive.filePath)
            updateState {
                copy(
                    nextVolume = NextVolumeInfo(
                        archiveId = archiveId,
                        fileName = nextArchive.fileName,
                        filePath = nextArchive.filePath,
                        firstEntryName = entries.firstOrNull(),
                    )
                )
            }
        }
    }

    private fun navigateToNextVolume() {
        val nextVol = currentState.nextVolume ?: return
        saveProgress()
        cancelAllAutoJobs()
        nextVolumeJob?.cancel()
        val pref = currentState.preference
        val processMode = currentState.autoProcessMode
        val tab = currentState.selectedBottomTab
        updateState {
            ViewerState(
                preference = pref,
                autoProcessMode = processMode,
                selectedBottomTab = tab,
            )
        }
        loadArchive(nextVol.archiveId, startPage = 0)
    }

    private data class TranslationCacheData(
        val blocks: List<TranslationBlock>,
        val imageWidth: Int,
        val imageHeight: Int,
        val colors: List<Color>,
    )

    /**
     * 번역 캐시 데이터를 조회·변환만 수행하고 결과를 반환한다 (상태 갱신 없음).
     */
    private suspend fun loadTranslationCacheData(archiveId: Long, page: Int): TranslationCacheData? {
        val isDoublePage = currentState.preference.viewMode == ViewMode.DOUBLE_PAGE

        if (isDoublePage) {
            val spread = pageToSpread(page)
            val (firstPage, secondPage) = spreadToPages(spread, currentState.totalPages)
            val isRtl = currentState.preference.readingDirection == ReadingDirection.RTL

            val firstCache = getPageTranslationCacheUseCase(archiveId, firstPage)
            val secondCache = if (secondPage != null) getPageTranslationCacheUseCase(archiveId, secondPage) else null

            if (firstCache == null && secondCache == null) return null
            if ((firstCache == null || firstCache.blocks.isEmpty()) &&
                (secondCache == null || secondCache.blocks.isEmpty())) return null

            val firstResult = firstCache?.takeIf { it.blocks.isNotEmpty() }?.let { cache ->
                val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
                val colors = restoreOrExtractColors(cache.blocks, textBlocks, firstPage)
                    ?: return@let null
                PageOcrResult(
                    firstPage, cache.imageWidth, cache.imageHeight, textBlocks,
                    cache.blocks.map { TranslationResult(it.originalText, it.translatedText) }, colors,
                )
            }
            val secondResult = secondCache?.takeIf { it.blocks.isNotEmpty() }?.let { cache ->
                val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
                val colors = restoreOrExtractColors(cache.blocks, textBlocks, secondPage!!)
                    ?: return@let null
                PageOcrResult(
                    secondPage, cache.imageWidth, cache.imageHeight, textBlocks,
                    cache.blocks.map { TranslationResult(it.originalText, it.translatedText) }, colors,
                )
            }

            if (firstResult == null && secondResult == null) return null

            // 한쪽만 번역이 있어도 스프레드 좌표계로 변환해야 함
            val combined = combinePageResults(
                firstResult ?: emptyPageOcrResult(firstPage),
                secondResult ?: (secondPage?.let { emptyPageOcrResult(it) }),
                isRtl,
            )

            val blocks = combined.ocrResults.zip(combined.translationResults).map { (ocr, tr) ->
                TranslationBlock(ocr.x, ocr.y, ocr.width, ocr.height, tr.originalText, tr.translatedText)
            }
            return TranslationCacheData(blocks, combined.imageWidth, combined.imageHeight, combined.overlayColors)
        } else {
            val cache = getPageTranslationCacheUseCase(archiveId, page) ?: return null
            if (cache.blocks.isEmpty()) return null

            val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
            val colors = restoreOrExtractColors(cache.blocks, textBlocks, page) ?: return null

            return TranslationCacheData(cache.blocks, cache.imageWidth, cache.imageHeight, colors)
        }
    }

    /**
     * NONE 모드 전용: 번역 캐시와 OCR 캐시를 한 코루틴에서 조회 후 원자적으로 상태 갱신.
     */
    private fun loadCachesForNoneMode(page: Int) {
        val archive = currentState.archive ?: return
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            try {
                val translationResult = loadTranslationCacheData(archive.id, page)
                val ocrCache = getPageOcrCacheUseCase(archive.id, page)

                updateState {
                    val withTranslation = if (translationResult != null) {
                        copy(
                            cachedBlocks = translationResult.blocks,
                            cachedImageWidth = translationResult.imageWidth,
                            cachedImageHeight = translationResult.imageHeight,
                            cachedOverlayColors = translationResult.colors,
                            selectedCachedBlockIndices = emptySet(),
                        )
                    } else {
                        copy(
                            cachedBlocks = emptyList(),
                            cachedImageWidth = 0,
                            cachedImageHeight = 0,
                            cachedOverlayColors = emptyList(),
                            selectedCachedBlockIndices = emptySet(),
                        )
                    }

                    if (ocrCache != null && ocrCache.blocks.isNotEmpty()) {
                        withTranslation.copy(
                            autoOcrBlocks = ocrCache.blocks,
                            autoOcrImageWidth = ocrCache.imageWidth,
                            autoOcrImageHeight = ocrCache.imageHeight,
                            autoProcessStatus = AutoProcessStatus.OCR_DONE,
                        )
                    } else {
                        withTranslation
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                clearCachedBlocks()
            }
        }
    }

    private fun cancelAllAutoJobs() {
        cacheJob?.cancel()
        autoOcrJob?.cancel()
        autoTranslateJob?.cancel()
        pageChangeDebounceJob?.cancel()
        selectedTranslateJob?.cancel()
    }

    private fun ViewerState.resetPageOcrStateKeepingCache() = copy(
        pipelineStage = PipelineStage.IDLE,
        cachedBlocks = emptyList(),
        cachedImageWidth = 0,
        cachedImageHeight = 0,
        cachedOverlayColors = emptyList(),
        selectedCachedBlockIndices = emptySet(),
        autoProcessStatus = AutoProcessStatus.IDLE,
        autoOcrBlocks = emptyList(),
        autoOcrImageWidth = 0,
        autoOcrImageHeight = 0,
        selectedOcrBlockIndices = emptySet(),
        autoTranslationBlocks = emptyList(),
        autoTranslationImageWidth = 0,
        autoTranslationImageHeight = 0,
        autoTranslationOverlayColors = emptyList(),
        isSelectedTranslationSheetVisible = false,
        selectedBlockTranslations = emptyList(),
        isSelectedTranslating = false,
    )

    private fun clearCachedBlocks() {
        updateState {
            copy(
                cachedBlocks = emptyList(),
                cachedImageWidth = 0,
                cachedImageHeight = 0,
                cachedOverlayColors = emptyList(),
                selectedCachedBlockIndices = emptySet(),
            )
        }
    }

    private fun ViewerState.resetPageOcrState() = copy(
        pipelineStage = PipelineStage.IDLE,
        // globalOverlayEnabled는 리셋하지 않음 (글로벌 상태)
        cachedBlocks = emptyList(),
        cachedImageWidth = 0,
        cachedImageHeight = 0,
        cachedOverlayColors = emptyList(),
        selectedCachedBlockIndices = emptySet(),
        autoProcessStatus = AutoProcessStatus.IDLE,
        autoOcrBlocks = emptyList(),
        autoOcrImageWidth = 0,
        autoOcrImageHeight = 0,
        selectedOcrBlockIndices = emptySet(),
        autoTranslationBlocks = emptyList(),
        autoTranslationImageWidth = 0,
        autoTranslationImageHeight = 0,
        autoTranslationOverlayColors = emptyList(),
        // 선택 번역 상태 리셋
        isSelectedTranslationSheetVisible = false,
        selectedBlockTranslations = emptyList(),
        isSelectedTranslating = false,
    )

    // ── 300ms 디바운스 ──────────────────────────────────────────────

    private fun scheduleAutoProcess(page: Int) {
        pageChangeDebounceJob?.cancel()
        pageChangeDebounceJob = viewModelScope.launch {
            delay(300)
            runAutoProcess(page)
        }
    }

    // ── 자동 OCR + 번역 ──────────────────────────────────────────────

    private fun runAutoProcess(page: Int) {
        autoOcrJob?.cancel()
        autoTranslateJob?.cancel()
        autoOcrJob = viewModelScope.launch {
            try {
                val state = currentState
                val archive = state.archive ?: return@launch
                val isDoublePage = state.preference.viewMode == ViewMode.DOUBLE_PAGE
                val needsTranslation = state.autoProcessMode == AutoProcessMode.AUTO_TRANSLATE ||
                    (state.autoProcessMode == AutoProcessMode.AUTO_OCR && state.globalOverlayEnabled)

                // 번역 캐시 우선 확인 — 캐시 히트 시 OCR 단계 건너뜀
                if (needsTranslation && tryApplyTranslationCache(archive.id, page, state)) {
                    return@launch
                }

                updateState { copy(autoProcessStatus = AutoProcessStatus.OCR_PROCESSING) }

                val ocrResult = if (isDoublePage) {
                    runAutoOcrDoublePageInternal(archive.id, page, state)
                } else {
                    runAutoOcrSinglePageInternal(archive.id, page)
                }

                if (ocrResult == null) {
                    updateState { copy(autoProcessStatus = AutoProcessStatus.OCR_FAILED) }
                    return@launch
                }

                updateState {
                    copy(
                        autoProcessStatus = AutoProcessStatus.OCR_DONE,
                        pipelineStage = PipelineStage.DONE,
                        autoOcrBlocks = ocrResult.blocks,
                        autoOcrImageWidth = ocrResult.imageWidth,
                        autoOcrImageHeight = ocrResult.imageHeight,
                    )
                }

                // OCR 완료 후 모드 재확인 — AUTO_TRANSLATE이면 번역 시작
                // AUTO_OCR + globalOverlayEnabled이면 오버레이용 번역도 트리거
                if (needsTranslation) {
                    runAutoTranslate(page, ocrResult)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateState { copy(autoProcessStatus = AutoProcessStatus.OCR_FAILED) }
            }
        }
    }

    /** 번역 캐시가 있으면 바로 적용하고 true 반환. 없으면 false. */
    private suspend fun tryApplyTranslationCache(archiveId: Long, page: Int, state: ViewerState): Boolean {
        val isDoublePage = state.preference.viewMode == ViewMode.DOUBLE_PAGE
        if (isDoublePage) {
            val spread = pageToSpread(page)
            val (firstPage, secondPage) = spreadToPages(spread, state.totalPages)
            val firstCache = getPageTranslationCacheUseCase(archiveId, firstPage)
            val secondCache = if (secondPage != null) getPageTranslationCacheUseCase(archiveId, secondPage) else null
            if (firstCache != null && (secondPage == null || secondCache != null)) {
                applyAutoTranslationFromCache(firstCache, secondCache, state)
                return true
            }
        } else {
            val cached = getPageTranslationCacheUseCase(archiveId, page)
            if (cached != null) {
                applyAutoTranslationFromSingleCache(cached)
                return true
            }
        }
        return false
    }

    private suspend fun runAutoOcrSinglePageInternal(archiveId: Long, page: Int): PageOcrCache? {
        // 캐시 확인
        val cached = getPageOcrCacheUseCase(archiveId, page) ?: getTranslationCacheAsOcr(archiveId, page)
        if (cached != null) return cached

        // 캐시 미스 — OCR 실행
        return processPageOcrOnly(page)
    }

    private suspend fun runAutoOcrDoublePageInternal(archiveId: Long, page: Int, state: ViewerState): PageOcrCache? {
        val spread = pageToSpread(page)
        val (firstPage, secondPage) = spreadToPages(spread, state.totalPages)
        val isRtl = state.preference.readingDirection == ReadingDirection.RTL

        val firstCached = getPageOcrCacheUseCase(archiveId, firstPage) ?: getTranslationCacheAsOcr(archiveId, firstPage)
        val secondCached = if (secondPage != null) getPageOcrCacheUseCase(archiveId, secondPage) ?: getTranslationCacheAsOcr(archiveId, secondPage) else null

        val firstResult = firstCached ?: processPageOcrOnly(firstPage)
        val secondResult = if (secondPage != null) secondCached ?: processPageOcrOnly(secondPage) else null

        if (firstResult == null && secondResult == null) return null

        return combineOcrBlocks(firstResult, secondResult, isRtl)
    }

    private fun runAutoTranslate(page: Int, ocrData: PageOcrCache) {
        autoTranslateJob?.cancel()
        autoTranslateJob = viewModelScope.launch {
            try {
                updateState {
                    copy(
                        autoProcessStatus = AutoProcessStatus.TRANSLATE_PROCESSING,
                        pipelineStage = PipelineStage.TRANSLATING,
                    )
                }

                val archive = currentState.archive ?: return@launch
                val isDoublePage = currentState.preference.viewMode == ViewMode.DOUBLE_PAGE

                // 양면 모드에서는 각 페이지별 번역 캐시 확인
                if (isDoublePage) {
                    val spread = pageToSpread(page)
                    val (firstPage, secondPage) = spreadToPages(spread, currentState.totalPages)
                    val firstCache = getPageTranslationCacheUseCase(archive.id, firstPage)
                    val secondCache = if (secondPage != null) getPageTranslationCacheUseCase(archive.id, secondPage) else null
                    if (firstCache != null && (secondPage == null || secondCache != null)) {
                        applyAutoTranslationFromCache(firstCache, secondCache, currentState)
                        return@launch
                    }
                } else {
                    val cached = getPageTranslationCacheUseCase(archive.id, page)
                    if (cached != null) {
                        applyAutoTranslationFromSingleCache(cached)
                        return@launch
                    }
                }

                // 캐시 미스 — 번역 실행
                val texts = ocrData.blocks.map { it.text }
                if (texts.isEmpty()) {
                    updateState {
                        copy(
                            autoProcessStatus = AutoProcessStatus.TRANSLATE_DONE,
                            pipelineStage = PipelineStage.DONE,
                        )
                    }
                    return@launch
                }

                if (isDoublePage) {
                    // 양면: 각 페이지를 별도 번역 + 캐시 저장
                    val spread = pageToSpread(page)
                    val (firstPage, secondPage) = spreadToPages(spread, currentState.totalPages)
                    translateAndCachePage(archive.id, firstPage)
                    if (secondPage != null) translateAndCachePage(archive.id, secondPage)
                    // 캐시에서 다시 로드하여 합성 좌표계로 적용
                    val firstCache = getPageTranslationCacheUseCase(archive.id, firstPage)
                    val secondCache = if (secondPage != null) getPageTranslationCacheUseCase(archive.id, secondPage) else null
                    if (firstCache != null) {
                        applyAutoTranslationFromCache(firstCache, secondCache, currentState)
                    } else {
                        updateState { copy(autoProcessStatus = AutoProcessStatus.TRANSLATE_DONE, pipelineStage = PipelineStage.DONE) }
                    }
                } else {
                    translateAndCachePage(archive.id, page)
                    val cached = getPageTranslationCacheUseCase(archive.id, page)
                    if (cached != null) {
                        applyAutoTranslationFromSingleCache(cached)
                    } else {
                        updateState { copy(autoProcessStatus = AutoProcessStatus.TRANSLATE_DONE, pipelineStage = PipelineStage.DONE) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateState { copy(autoProcessStatus = AutoProcessStatus.TRANSLATE_FAILED) }
            }
        }
    }

    /** 단일 페이지에 대해 OCR 캐시 기반으로 번역 + 캐시 저장. */
    private suspend fun translateAndCachePage(archiveId: Long, pageIndex: Int) {
        // 이미 번역 캐시가 있으면 스킵
        val existing = getPageTranslationCacheUseCase(archiveId, pageIndex)
        if (existing != null) return

        val ocrCache = getPageOcrCacheUseCase(archiveId, pageIndex) ?: return
        if (ocrCache.blocks.isEmpty()) return

        val textBlocks = ocrCache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.text) }
        val translationResults = translateTextUseCase(textBlocks.map { it.text })
        val imageBytes = getImageBytes(pageIndex) ?: return
        val colors = extractDominantColors(imageBytes, textBlocks)

        saveFinalCache(archiveId, pageIndex, ocrCache.imageWidth, ocrCache.imageHeight, textBlocks, translationResults, colors)
    }

    private suspend fun applyAutoTranslationFromSingleCache(cache: PageTranslationCache) {
        val textBlocks = cache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
        val colors = restoreOrExtractColors(cache.blocks, textBlocks, cache.page) ?: emptyList()

        updateState {
            copy(
                autoProcessStatus = AutoProcessStatus.TRANSLATE_DONE,
                pipelineStage = PipelineStage.DONE,
                autoTranslationBlocks = cache.blocks,
                autoTranslationImageWidth = cache.imageWidth,
                autoTranslationImageHeight = cache.imageHeight,
                autoTranslationOverlayColors = colors,
            )
        }
    }

    private suspend fun applyAutoTranslationFromCache(
        firstCache: PageTranslationCache,
        secondCache: PageTranslationCache?,
        state: ViewerState,
    ) {
        val isRtl = state.preference.readingDirection == ReadingDirection.RTL

        val firstBlocks = firstCache.blocks
        val secondBlocks = secondCache?.blocks ?: emptyList()

        if (firstBlocks.isEmpty() && secondBlocks.isEmpty()) {
            updateState {
                copy(
                    autoProcessStatus = AutoProcessStatus.TRANSLATE_DONE,
                    pipelineStage = PipelineStage.DONE,
                )
            }
            return
        }

        // 양면 합성 좌표계로 변환
        val leftCache = if (isRtl && secondCache != null) secondCache else firstCache
        val rightCache = if (isRtl) firstCache else secondCache

        // secondPage가 없는 스프레드(홀수 마지막 페이지)인지 확인
        val spread = pageToSpread(state.currentPage)
        val (_, secondPage) = spreadToPages(spread, state.totalPages)

        if (rightCache == null && secondPage == null) {
            // 실제로 두 번째 페이지가 없는 스프레드 → 단면 그대로
            applyAutoTranslationFromSingleCache(leftCache)
            return
        }

        // rightCache가 null이면 해당 페이지의 이미지 크기를 조회하여 스프레드 좌표계 계산
        val rightImageW: Int
        val rightImageH: Int
        if (rightCache != null) {
            rightImageW = rightCache.imageWidth
            rightImageH = rightCache.imageHeight
        } else {
            val missingPage = if (isRtl) firstCache.page else secondPage!!
            val bytes = getImageBytes(missingPage)
            if (bytes != null) {
                val (w, h) = getImageDimensions(bytes)
                rightImageW = w
                rightImageH = h
            } else {
                applyAutoTranslationFromSingleCache(leftCache)
                return
            }
        }

        val targetH = maxOf(leftCache.imageHeight, rightImageH)
        val leftScale = targetH.toFloat() / leftCache.imageHeight
        val rightScale = targetH.toFloat() / rightImageH
        val scaledLeftW = (leftCache.imageWidth * leftScale).toInt()

        val scaledLeftBlocks = leftCache.blocks.map { b ->
            TranslationBlock(
                x = (b.x * leftScale).toInt(), y = (b.y * leftScale).toInt(),
                width = (b.width * leftScale).toInt(), height = (b.height * leftScale).toInt(),
                originalText = b.originalText, translatedText = b.translatedText,
                dominantColorArgb = b.dominantColorArgb,
            )
        }
        val scaledRightBlocks = (rightCache?.blocks ?: emptyList()).map { b ->
            TranslationBlock(
                x = (b.x * rightScale).toInt() + scaledLeftW, y = (b.y * rightScale).toInt(),
                width = (b.width * rightScale).toInt(), height = (b.height * rightScale).toInt(),
                originalText = b.originalText, translatedText = b.translatedText,
                dominantColorArgb = b.dominantColorArgb,
            )
        }

        val allBlocks = scaledLeftBlocks + scaledRightBlocks
        val leftTextBlocks = leftCache.blocks.map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
        val rightTextBlocks = (rightCache?.blocks ?: emptyList()).map { TextBlock(it.x, it.y, it.width, it.height, it.originalText) }
        val leftColors = restoreOrExtractColors(leftCache.blocks, leftTextBlocks, leftCache.page) ?: emptyList()
        val rightColors = if (rightCache != null && rightTextBlocks.isNotEmpty()) {
            restoreOrExtractColors(rightCache.blocks, rightTextBlocks, rightCache.page) ?: emptyList()
        } else {
            emptyList()
        }

        updateState {
            copy(
                autoProcessStatus = AutoProcessStatus.TRANSLATE_DONE,
                pipelineStage = PipelineStage.DONE,
                autoTranslationBlocks = allBlocks,
                autoTranslationImageWidth = scaledLeftW + (rightImageW * rightScale).toInt(),
                autoTranslationImageHeight = targetH,
                autoTranslationOverlayColors = leftColors + rightColors,
            )
        }
    }

    // ── 모드 전환 핸들러 ──────────────────────────────────────────────

    private fun handleSetAutoProcessMode(newMode: AutoProcessMode) {
        val oldMode = currentState.autoProcessMode

        when {
            // * → NONE: 모든 작업 취소
            newMode == AutoProcessMode.NONE -> {
                autoOcrJob?.cancel()
                autoTranslateJob?.cancel()
                pageChangeDebounceJob?.cancel()
                updateState {
                    copy(
                        autoProcessMode = AutoProcessMode.NONE,
                        autoProcessStatus = AutoProcessStatus.IDLE,
                        autoOcrBlocks = emptyList(),
                        autoOcrImageWidth = 0,
                        autoOcrImageHeight = 0,
                        selectedOcrBlockIndices = emptySet(),
                        autoTranslationBlocks = emptyList(),
                        autoTranslationImageWidth = 0,
                        autoTranslationImageHeight = 0,
                        autoTranslationOverlayColors = emptyList(),
                    )
                }
            }

            // NONE → OCR/TRANSLATE: 처음부터 시작
            oldMode == AutoProcessMode.NONE -> {
                updateState { copy(autoProcessMode = newMode) }
                runAutoProcess(currentState.currentPage)
            }

            // AUTO_OCR → AUTO_TRANSLATE: OCR 유지, 번역 추가
            oldMode == AutoProcessMode.AUTO_OCR && newMode == AutoProcessMode.AUTO_TRANSLATE -> {
                updateState { copy(autoProcessMode = newMode) }
                val status = currentState.autoProcessStatus
                when {
                    status == AutoProcessStatus.OCR_DONE -> {
                        // OCR 완료 — 즉시 번역 시작
                        val ocrData = buildOcrDataFromCurrentState()
                        if (ocrData != null) {
                            runAutoTranslate(currentState.currentPage, ocrData)
                        } else {
                            runAutoProcess(currentState.currentPage)
                        }
                    }
                    status == AutoProcessStatus.OCR_PROCESSING -> {
                        // OCR 진행 중 — runAutoProcess가 완료 후 자동으로 번역 시작
                    }
                    else -> {
                        // IDLE 또는 FAILED — 처음부터 시작
                        runAutoProcess(currentState.currentPage)
                    }
                }
            }

            // AUTO_TRANSLATE → AUTO_OCR: OCR 유지, 번역 API 호출 금지
            oldMode == AutoProcessMode.AUTO_TRANSLATE && newMode == AutoProcessMode.AUTO_OCR -> {
                updateState { copy(autoProcessMode = newMode) }
                val status = currentState.autoProcessStatus
                when {
                    status == AutoProcessStatus.TRANSLATE_PROCESSING -> {
                        // 이미 호출된 번역 API는 취소하지 않고 기존대로 진행
                        // 완료 후 상태는 UI에서 OCR 모드 기준으로 표시
                    }
                    status == AutoProcessStatus.TRANSLATE_DONE || status == AutoProcessStatus.OCR_DONE -> {
                        updateState { copy(autoProcessStatus = AutoProcessStatus.OCR_DONE) }
                    }
                    status == AutoProcessStatus.TRANSLATE_FAILED -> {
                        updateState { copy(autoProcessStatus = AutoProcessStatus.OCR_DONE) }
                    }
                    else -> {
                        // OCR_PROCESSING 등 — 그대로 진행
                    }
                }
            }
        }
    }

    private fun handleRetryAutoProcess() {
        val status = currentState.autoProcessStatus
        when {
            status == AutoProcessStatus.OCR_FAILED -> {
                runAutoProcess(currentState.currentPage)
            }
            status == AutoProcessStatus.TRANSLATE_FAILED -> {
                val ocrData = buildOcrDataFromCurrentState()
                if (ocrData != null) {
                    runAutoTranslate(currentState.currentPage, ocrData)
                } else {
                    runAutoProcess(currentState.currentPage)
                }
            }
        }
    }

    private fun buildOcrDataFromCurrentState(): PageOcrCache? {
        val state = currentState
        if (state.autoOcrBlocks.isEmpty()) return null
        return PageOcrCache(
            id = 0,
            archiveId = state.archive?.id ?: return null,
            page = state.currentPage,
            imageWidth = state.autoOcrImageWidth,
            imageHeight = state.autoOcrImageHeight,
            blocks = state.autoOcrBlocks,
            createdAt = 0,
        )
    }

    /** 양면 모드에서 두 페이지의 OcrBlock을 합성 좌표계로 변환한다. */
    private fun combineOcrBlocks(
        firstResult: PageOcrCache?,
        secondResult: PageOcrCache?,
        isRtl: Boolean,
    ): PageOcrCache {
        if (firstResult == null && secondResult != null) return secondResult
        if (secondResult == null && firstResult != null) return firstResult
        val first = firstResult!!
        val second = secondResult!!

        val leftResult = if (isRtl) second else first
        val rightResult = if (isRtl) first else second

        val targetH = maxOf(leftResult.imageHeight, rightResult.imageHeight)
        val leftScale = targetH.toFloat() / leftResult.imageHeight
        val rightScale = targetH.toFloat() / rightResult.imageHeight
        val scaledLeftW = (leftResult.imageWidth * leftScale).toInt()
        val scaledRightW = (rightResult.imageWidth * rightScale).toInt()

        val scaledLeftBlocks = leftResult.blocks.map { b ->
            OcrBlock(
                x = (b.x * leftScale).toInt(),
                y = (b.y * leftScale).toInt(),
                width = (b.width * leftScale).toInt(),
                height = (b.height * leftScale).toInt(),
                text = b.text,
                dominantColorArgb = b.dominantColorArgb,
            )
        }

        val scaledRightBlocks = rightResult.blocks.map { b ->
            OcrBlock(
                x = (b.x * rightScale).toInt() + scaledLeftW,
                y = (b.y * rightScale).toInt(),
                width = (b.width * rightScale).toInt(),
                height = (b.height * rightScale).toInt(),
                text = b.text,
                dominantColorArgb = b.dominantColorArgb,
            )
        }

        return PageOcrCache(
            id = 0,
            archiveId = first.archiveId,
            page = first.page,
            imageWidth = scaledLeftW + scaledRightW,
            imageHeight = targetH,
            blocks = scaledLeftBlocks + scaledRightBlocks,
            createdAt = first.createdAt,
        )
    }

    /** 번역 캐시에서 OCR 데이터만 추출. 캐시 미스 시 null. */
    private suspend fun getTranslationCacheAsOcr(archiveId: Long, page: Int): PageOcrCache? {
        val cache = getPageTranslationCacheUseCase(archiveId, page) ?: return null
        return PageOcrCache(
            id = cache.id,
            archiveId = cache.archiveId,
            page = cache.page,
            imageWidth = cache.imageWidth,
            imageHeight = cache.imageHeight,
            blocks = cache.blocks.map {
                OcrBlock(it.x, it.y, it.width, it.height, it.originalText, it.dominantColorArgb)
            },
            createdAt = cache.createdAt,
        )
    }

    // ── OCR + 번역 파이프라인 ────────────────────────────────────

    /**
     * 단일 페이지에 대해 OCR + 번역을 수행하고 캐시에 저장한다.
     * 자동 OCR이 켜져 있으면 OCR 캐시를 재활용하여 번역만 실행한다.
     */
    private suspend fun processPageOcr(pageIndex: Int): PageOcrResult? {
        val state = currentState
        val archive = state.archive ?: return null

        // 1. 번역 캐시 먼저 확인 (OCR + 번역 결과가 모두 있음)
        val cached = getPageTranslationCacheUseCase(archive.id, pageIndex)
        if (cached != null) {
            val ocrResults = cached.blocks.map { block ->
                TextBlock(block.x, block.y, block.width, block.height, block.originalText)
            }
            val translationResults = cached.blocks.map { block ->
                TranslationResult(block.originalText, block.translatedText)
            }
            val hasColors = cached.blocks.all { it.dominantColorArgb != null }
            val colors = if (hasColors && cached.blocks.isNotEmpty()) {
                cached.blocks.map { Color(it.dominantColorArgb!!) }
            } else if (cached.blocks.isNotEmpty()) {
                val imageBytes = getImageBytes(pageIndex) ?: return null
                extractDominantColors(imageBytes, ocrResults)
            } else {
                emptyList()
            }
            return PageOcrResult(
                pageIndex = pageIndex,
                imageWidth = cached.imageWidth,
                imageHeight = cached.imageHeight,
                ocrResults = ocrResults,
                translationResults = translationResults,
                overlayColors = colors,
            )
        }

        // 2. 자동 OCR 진행 중이면 완료 대기
        if (state.autoProcessMode != AutoProcessMode.NONE && autoOcrJob?.isActive == true) {
            autoOcrJob?.join()
        }

        // 3. OCR 전용 캐시 확인 (자동 OCR 또는 이전 실행으로 저장된 것)
        val ocrCache = getPageOcrCacheUseCase(archive.id, pageIndex)
        if (ocrCache != null && ocrCache.blocks.isNotEmpty()) {
            return ocrCacheToFullResult(archive.id, pageIndex, ocrCache)
        }

        // 4. 캐시 미스 — OCR + 번역 전체 실행
        return runFullOcrAndTranslate(archive.id, pageIndex)
    }

    /** OCR 캐시가 있을 때 번역만 추가로 실행하여 PageOcrResult를 생성한다. */
    private suspend fun ocrCacheToFullResult(
        archiveId: Long, pageIndex: Int, ocrCache: PageOcrCache,
    ): PageOcrResult? {
        val ocrResults = ocrCache.blocks.map { block ->
            TextBlock(block.x, block.y, block.width, block.height, block.text)
        }
        val imgW = ocrCache.imageWidth
        val imgH = ocrCache.imageHeight

        val translationResults = if (ocrResults.isNotEmpty()) {
            translateTextUseCase(ocrResults.map { it.text })
        } else {
            emptyList()
        }

        val imageBytes = getImageBytes(pageIndex) ?: return null
        val colors = if (ocrResults.isNotEmpty()) {
            extractDominantColors(imageBytes, ocrResults)
        } else {
            emptyList()
        }

        // 전체 번역 캐시 저장
        saveFinalCache(archiveId, pageIndex, imgW, imgH, ocrResults, translationResults, colors)

        return PageOcrResult(
            pageIndex = pageIndex, imageWidth = imgW, imageHeight = imgH,
            ocrResults = ocrResults, translationResults = translationResults,
            overlayColors = colors,
        )
    }

    /** OCR 캐시도 없을 때 처음부터 OCR + 번역을 실행한다. */
    private suspend fun runFullOcrAndTranslate(archiveId: Long, pageIndex: Int): PageOcrResult? {
        val imageBytes = getImageBytes(pageIndex) ?: return null
        val (imgW, imgH) = getImageDimensions(imageBytes)

        val ocrResults = recognizePageTextUseCase(imageBytes)

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

        saveFinalCache(archiveId, pageIndex, imgW, imgH, ocrResults, translationResults, colors)

        return PageOcrResult(
            pageIndex = pageIndex, imageWidth = imgW, imageHeight = imgH,
            ocrResults = ocrResults, translationResults = translationResults,
            overlayColors = colors,
        )
    }

    /** OCR + 번역 결과를 PageTranslationCache로 저장한다. */
    private suspend fun saveFinalCache(
        archiveId: Long, pageIndex: Int,
        imgW: Int, imgH: Int,
        ocrResults: List<TextBlock>,
        translationResults: List<TranslationResult>,
        colors: List<Color>,
    ) {
        val blocks = ocrResults.zip(translationResults).mapIndexed { i, (ocr, tr) ->
            TranslationBlock(
                x = ocr.x, y = ocr.y, width = ocr.width, height = ocr.height,
                originalText = tr.originalText,
                translatedText = tr.translatedText,
                dominantColorArgb = colors.getOrNull(i)?.let { color ->
                    ((color.alpha * 255).toInt() shl 24) or
                        ((color.red * 255).toInt() shl 16) or
                        ((color.green * 255).toInt() shl 8) or
                        (color.blue * 255).toInt()
                },
            )
        }
        savePageTranslationCacheUseCase(
            PageTranslationCache(
                archiveId = archiveId, page = pageIndex,
                imageWidth = imgW, imageHeight = imgH,
                blocks = blocks, createdAt = Clock.System.now().toEpochMilliseconds(),
            )
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
    private suspend fun emptyPageOcrResult(pageIndex: Int): PageOcrResult {
        val bytes = getImageBytes(pageIndex)
        val (w, h) = if (bytes != null) getImageDimensions(bytes) else Pair(0, 0)
        return PageOcrResult(pageIndex, w, h, emptyList(), emptyList(), emptyList())
    }

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

}
