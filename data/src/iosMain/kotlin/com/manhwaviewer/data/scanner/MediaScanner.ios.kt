package com.manhwaviewer.data.scanner

import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.cinterop.*
import kotlin.time.Clock
import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

actual class MediaScanner {

    private val archiveExtensions = setOf("zip", "cbz", "cbr")

    actual fun scanDirectory(directoryPath: String): List<ArchiveFile> {
        val fileManager = NSFileManager.defaultManager
        val dirUrl = NSURL.fileURLWithPath(directoryPath)
        val prefetchKeys = listOf(NSURLIsDirectoryKey, NSURLContentModificationDateKey)

        @Suppress("UNCHECKED_CAST")
        val urls = fileManager.contentsOfDirectoryAtURL(
            dirUrl,
            includingPropertiesForKeys = prefetchKeys,
            options = 0u,
            error = null,
        ) as? List<NSURL> ?: return emptyList()

        val results = mutableListOf<ArchiveFile>()

        for (url in urls) {
            val name = url.lastPathComponent ?: continue
            val fullPath = url.path ?: continue

            val resourceValues = url.resourceValuesForKeys(prefetchKeys, error = null)
            val isDir = resourceValues?.get(NSURLIsDirectoryKey) as? Boolean ?: false
            val modDate = resourceValues?.get(NSURLContentModificationDateKey) as? NSDate
            val modifiedAt = if (modDate != null) {
                Instant.fromEpochMilliseconds((modDate.timeIntervalSince1970 * 1000).toLong())
            } else {
                Clock.System.now()
            }

            if (isDir) {
                results.add(
                    ArchiveFile(
                        filePath = fullPath,
                        fileName = name,
                        type = ArchiveType.FOLDER,
                        totalPages = 0,
                        addedAt = modifiedAt,
                        hasImages = false,
                    ),
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
                            addedAt = modifiedAt,
                            hasImages = false,
                        ),
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

    actual fun getVaultPath(): String {
        val fileManager = NSFileManager.defaultManager
        val appSupportUrl = fileManager.URLForDirectory(
            directory = platform.Foundation.NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val vaultPath = requireNotNull(appSupportUrl?.path) { "NSApplicationSupportDirectory not found" } + "/vault"
        if (!fileManager.fileExistsAtPath(vaultPath)) {
            fileManager.createDirectoryAtPath(
                vaultPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        return vaultPath
    }

    actual fun moveFile(sourcePath: String, destinationDir: String): Result<String> {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(sourcePath)) {
            return Result.failure(IllegalStateException("File not found: $sourcePath"))
        }
        if (!fileManager.fileExistsAtPath(destinationDir)) {
            fileManager.createDirectoryAtPath(
                destinationDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        val fileName = sourcePath.substringAfterLast('/')
        val destPath = "$destinationDir/$fileName"
        if (fileManager.fileExistsAtPath(destPath)) {
            return Result.failure(IllegalStateException("File already exists: $destPath"))
        }
        val success = fileManager.moveItemAtPath(sourcePath, toPath = destPath, error = null)
        return if (success) {
            Result.success(destPath)
        } else {
            Result.failure(IllegalStateException("Failed to move file"))
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

    actual fun createFolder(parentPath: String, folderName: String): Result<String> {
        val fileManager = NSFileManager.defaultManager
        val newPath = "$parentPath/$folderName"
        if (fileManager.fileExistsAtPath(newPath)) {
            return Result.failure(IllegalStateException("Folder already exists: $newPath"))
        }
        val success = fileManager.createDirectoryAtPath(
            newPath,
            withIntermediateDirectories = false,
            attributes = null,
            error = null,
        )
        return if (success) {
            Result.success(newPath)
        } else {
            Result.failure(IllegalStateException("Failed to create folder"))
        }
    }

    actual fun deleteFile(path: String): Result<Unit> {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            return Result.failure(IllegalStateException("File not found: $path"))
        }
        val success = fileManager.removeItemAtPath(path, error = null)
        return if (success) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to delete file"))
        }
    }

    actual fun isDirectory(path: String): Boolean {
        val fileManager = NSFileManager.defaultManager
        return memScoped {
            val isDir = alloc<BooleanVar>()
            fileManager.fileExistsAtPath(path, isDirectory = isDir.ptr) && isDir.value
        }
    }

    actual fun listFilesRecursively(directoryPath: String): List<String> {
        val fileManager = NSFileManager.defaultManager
        val enumerator = fileManager.enumeratorAtPath(directoryPath) ?: return emptyList()
        val result = mutableListOf<String>()
        var item = enumerator.nextObject()
        while (item != null) {
            val relativePath = item as String
            val fullPath = "$directoryPath/$relativePath"
            val isDir = memScoped {
                val dir = alloc<BooleanVar>()
                fileManager.fileExistsAtPath(fullPath, isDirectory = dir.ptr) && dir.value
            }
            if (!isDir) {
                result.add(fullPath)
            }
            item = enumerator.nextObject()
        }
        return result
    }

    actual fun ensureDirectoryExists(path: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            // 실패 시 후속 moveFile에서 명확한 에러가 발생하므로 여기서는 best-effort
            fileManager.createDirectoryAtPath(
                path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }
}
