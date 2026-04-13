package com.manhwaviewer.data.scanner

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

actual class MediaScanner {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val archiveExtensions = setOf("zip", "cbz", "cbr")

    actual fun scanDirectory(directoryPath: String): List<ArchiveFile> {
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(directoryPath, error = null)
            ?: return emptyList()

        val results = mutableListOf<ArchiveFile>()

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
                        addedAt = getFileModifiedAt(fullPath),
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
                            addedAt = getFileModifiedAt(fullPath),
                            hasImages = false,
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

    private fun getFileModifiedAt(path: String): Instant {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        val date = attrs?.get(NSFileModificationDate) as? NSDate
        return if (date != null) {
            Instant.fromEpochMilliseconds((date.timeIntervalSince1970 * 1000).toLong())
        } else {
            Clock.System.now()
        }
    }

    actual fun renameFile(oldPath: String, newName: String): Result<String> {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(oldPath)) {
            return Result.failure(IllegalStateException("File not found: $oldPath"))
        }
        val parentPath = oldPath.substringBeforeLast('/')
        val newPath = "$parentPath/$newName"
        if (fileManager.fileExistsAtPath(newPath)) {
            return Result.failure(IllegalStateException("File already exists: $newPath"))
        }
        val success = fileManager.moveItemAtPath(oldPath, toPath = newPath, error = null)
        return if (success) {
            Result.success(newPath)
        } else {
            Result.failure(IllegalStateException("Failed to rename file"))
        }
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
