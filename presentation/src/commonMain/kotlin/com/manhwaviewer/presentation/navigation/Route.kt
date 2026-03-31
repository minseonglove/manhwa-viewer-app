package com.manhwaviewer.presentation.navigation

object Route {
    const val HOME = "home"
    const val VIEWER = "viewer/{archiveId}?startPage={startPage}&sortByDate={sortByDate}&sortAscending={sortAscending}"
    const val SETTINGS = "settings"
    const val FILE_BROWSER = "file_browser"
    const val RECENT = "recent"
    const val IMAGE_GALLERY = "image_gallery"
    const val VAULT = "vault"

    fun viewer(archiveId: Long, startPage: Int = -1, sortByDate: Boolean = false, sortAscending: Boolean = true): String =
        "viewer/$archiveId?startPage=$startPage&sortByDate=$sortByDate&sortAscending=$sortAscending"
}
