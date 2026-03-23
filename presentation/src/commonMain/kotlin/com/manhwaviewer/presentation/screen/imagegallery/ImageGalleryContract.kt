package com.manhwaviewer.presentation.screen.imagegallery

import com.manhwaviewer.domain.entity.ImageFile
import com.manhwaviewer.presentation.screen.common.SortState

data class ImageGalleryState(
    val images: List<ImageFile> = emptyList(),
    val sortState: SortState = SortState(),
    val isLoading: Boolean = true,
)

sealed interface ImageGalleryIntent {
    data class SetSortField(val field: com.manhwaviewer.presentation.screen.common.SortField) : ImageGalleryIntent
    data object ToggleSortDirection : ImageGalleryIntent
    data object ToggleViewMode : ImageGalleryIntent
}

sealed interface ImageGallerySideEffect
