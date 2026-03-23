package com.manhwaviewer.presentation.screen.imagegallery

import androidx.lifecycle.viewModelScope
import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.domain.usecase.GetDeviceImagesUseCase
import com.manhwaviewer.presentation.mvi.MviViewModel
import com.manhwaviewer.presentation.screen.common.ListViewMode
import com.manhwaviewer.presentation.screen.common.SortField
import kotlinx.coroutines.launch

class ImageGalleryViewModel(
    getDeviceImagesUseCase: GetDeviceImagesUseCase,
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
