package com.manhwaviewer.presentation.screen.imagegallery

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.domain.usecase.GetImageEntriesUseCase
import com.manhwaviewer.domain.usecase.UpsertArchiveUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import kotlinx.coroutines.launch
import kotlin.time.Instant

class ImageGalleryViewModel(
    getDeviceImagesUseCase: GetDeviceImagesUseCase,
    private val upsertArchiveUseCase: UpsertArchiveUseCase,
    private val getImageEntriesUseCase: GetImageEntriesUseCase,
) : MviViewModel<ImageGalleryState, ImageGalleryIntent, ImageGallerySideEffect>(ImageGalleryState()) {

    init {
        viewModelScope.launch {
            getDeviceImagesUseCase().collect { images ->
                updateState { copy(images = sortImages(images), isLoading = false) }
            }
        }
    }

    override fun handleIntent(intent: ImageGalleryIntent) {
        when (intent) {
            is ImageGalleryIntent.SetSortField -> {
                updateState { copy(sortState = sortState.copy(field = intent.field)) }
                resortImages()
            }
            ImageGalleryIntent.ToggleSortDirection -> {
                updateState { copy(sortState = sortState.copy(ascending = !sortState.ascending)) }
                resortImages()
            }
            ImageGalleryIntent.ToggleViewMode -> {
                updateState {
                    copy(
                        sortState = sortState.copy(
                            viewMode = if (sortState.viewMode == ListViewMode.LIST) ListViewMode.GRID else ListViewMode.LIST,
                        ),
                    )
                }
            }
            is ImageGalleryIntent.OpenImage -> openImage(intent.image)
        }
    }

    private fun openImage(image: ImageFile) {
        viewModelScope.launch {
            val parentPath = image.filePath.substringBeforeLast('/')
            val entries = getImageEntriesUseCase(parentPath)
            val pageIndex = entries.indexOf(image.fileName).coerceAtLeast(0)
            val archive = ArchiveFile(
                filePath = parentPath,
                fileName = parentPath.substringAfterLast('/'),
                type = ArchiveType.FOLDER,
                totalPages = entries.size,
                lastReadIndex = pageIndex,
                addedAt = Instant.fromEpochMilliseconds(
                    kotlin.time.Clock.System.now().toEpochMilliseconds()
                ),
                hasImages = true,
            )
            val id = upsertArchiveUseCase(archive)
            val sortByDate = currentState.sortState.field == SortField.DATE
            val sortAscending = currentState.sortState.ascending
            sendSideEffect(ImageGallerySideEffect.NavigateToViewer(id, pageIndex, sortByDate, sortAscending))
        }
    }

    private fun resortImages() {
        updateState { copy(images = sortImages(images)) }
    }

    private fun sortImages(images: List<ImageFile>): List<ImageFile> {
        val comparator = when (currentState.sortState.field) {
            SortField.NAME -> compareBy<ImageFile> { it.fileName.lowercase() }
            SortField.DATE -> compareBy { it.dateModified }
        }
        return if (currentState.sortState.ascending) {
            images.sortedWith(comparator)
        } else {
            images.sortedWith(comparator.reversed())
        }
    }
}
