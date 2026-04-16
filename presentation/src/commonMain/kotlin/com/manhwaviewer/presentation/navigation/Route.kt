package com.manhwaviewer.presentation.navigation

object Route {
    const val HOME = "home"
    const val VIEWER = "viewer/{archiveId}?startPage={startPage}&sortByDate={sortByDate}&sortAscending={sortAscending}"
    const val SETTINGS = "settings"
    const val FILE_BROWSER = "file_browser?initialPath={initialPath}"
    const val RECENT = "recent"
    const val IMAGE_GALLERY = "image_gallery"
    const val VAULT = "vault"
    const val FILE_MOVE_SELECT = "file_move_select"
    const val SEARCH = "search"

    fun fileBrowser(initialPath: String? = null): String =
        if (initialPath != null) "file_browser?initialPath=${encodePathComponent(initialPath)}"
        else "file_browser"

    fun viewer(archiveId: Long, startPage: Int = -1, sortByDate: Boolean = false, sortAscending: Boolean = true): String =
        "viewer/$archiveId?startPage=$startPage&sortByDate=$sortByDate&sortAscending=$sortAscending"

    private fun encodePathComponent(value: String): String =
        value.replace("%", "%25")
            .replace("/", "%2F")
            .replace("?", "%3F")
            .replace("#", "%23")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace(" ", "%20")
}
