package com.manhwaviewer.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    @Serializable
    data object Home : Route

    @Serializable
    data class Viewer(
        val archiveId: Long,
        val startPage: Int = -1,
        val sortByDate: Boolean = false,
        val sortAscending: Boolean = true,
    ) : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class FileBrowser(
        val initialPath: String? = null,
    ) : Route

    @Serializable
    data object FileMoveSelect : Route

    @Serializable
    data object Recent : Route

    @Serializable
    data object ImageGallery : Route

    @Serializable
    data object Search : Route

    @Serializable
    data object Vault : Route
}
