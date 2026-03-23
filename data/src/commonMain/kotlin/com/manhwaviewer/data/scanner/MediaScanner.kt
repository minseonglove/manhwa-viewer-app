package com.manhwaviewer.data.scanner

import com.manhwaviewer.domain.entity.ArchiveFile

expect class MediaScanner {
    fun scanDirectory(directoryPath: String): List<ArchiveFile>
    fun getDefaultScanPath(): String
}
