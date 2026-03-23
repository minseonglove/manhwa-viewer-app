package com.manhwaviewer.data.scanner

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.datetime.Clock
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual class MediaScanner {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val archiveExtensions = setOf("zip", "cbz", "cbr")

    actual fun scanDirectory(directoryPath: String): List<ArchiveFile> {
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(directoryPath, error = null)
            ?: return emptyList()

        val results = mutableListOf<ArchiveFile>()
        val now = Clock.System.now()

        for (i in 0 until contents.count.toInt()) {
            val name = contents.objectAtIndex(i.toULong()) as? String ?: continue
            val fullPath = "$directoryPath/$name"

            var isDir = false
            memScoped {
                val isDirPtr = alloc<BooleanVar>()
                fileManager.fileExistsAtPath(fullPath, isDirectory = isDirPtr.ptr)
                isDir = isDirPtr.value
            }

            if (isDir) {
                val imageCount = countImages(fullPath)
                results.add(
                    ArchiveFile(
                        filePath = fullPath,
                        fileName = name,
                        type = ArchiveType.FOLDER,
                        totalPages = imageCount,
                        addedAt = now,
                        hasImages = imageCount > 0,
                    )
                )
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in archiveExtensions) {
                    results.add(
                        ArchiveFile(
                            filePath = fullPath,
                            fileName = name,
                            type = when (ext) {
                                "cbz" -> ArchiveType.CBZ
                                "cbr" -> ArchiveType.CBR
                                else -> ArchiveType.ZIP
                            },
                            addedAt = now,
                            hasImages = true,
                        )
                    )
                }
            }
        }

        return results
    }

    actual fun getDefaultScanPath(): String {
        val docDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(docDir?.path) { "NSDocumentDirectory not found" }
    }

    private fun countImages(folderPath: String): Int {
        val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(folderPath, error = null)
            ?: return 0
        var count = 0
        for (i in 0 until contents.count.toInt()) {
            val name = contents.objectAtIndex(i.toULong()) as? String ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in imageExtensions) count++
        }
        return count
    }
}
