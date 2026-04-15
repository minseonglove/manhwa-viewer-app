package com.manhwaviewer.presentation.screen.filemoveselect

class MoveRequestHolder {
    var sourcePaths: List<String> = emptyList()
    var startPath: String = ""

    fun clear() {
        sourcePaths = emptyList()
        startPath = ""
    }
}
