package com.manhwaviewer.data.scanner

import android.content.Context
import android.os.Environment
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

actual class MediaScanner(private val context: Context) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val archiveExtensions = setOf("zip", "cbz", "cbr")

    actual fun scanDirectory(directoryPath: String): List<ArchiveFile> {
        val dir = Paths.get(directoryPath)
        if (!Files.exists(dir) || !Files.isDirectory(dir)) return emptyList()

        val results = mutableListOf<ArchiveFile>()

        try {
            Files.newDirectoryStream(dir).use { stream ->
                for (entry in stream) {
                    val attrs = Files.readAttributes(entry, BasicFileAttributes::class.java)
                    val name = entry.fileName.toString()
                    val fullPath = entry.toAbsolutePath().toString()
                    val modifiedAt = Instant.fromEpochMilliseconds(attrs.lastModifiedTime().toMillis())

                    when {
                        attrs.isDirectory -> {
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
                        }
                        attrs.isRegularFile -> {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            when {
                                ext in archiveExtensions -> {
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
                                ext in imageExtensions -> {
                                    results.add(
                                        ArchiveFile(
                                            filePath = fullPath,
                                            fileName = name,
                                            type = ArchiveType.IMAGE,
                                            addedAt = modifiedAt,
                                            hasImages = false,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return results
    }

    @Suppress("DEPRECATION")
    actual fun getDefaultScanPath(): String =
        Environment.getExternalStorageDirectory().absolutePath

    actual fun renameFile(oldPath: String, newName: String): Result<String> {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return Result.failure(IllegalStateException("File not found: $oldPath"))
        val newFile = File(oldFile.parentFile, newName)
        if (newFile.exists()) return Result.failure(IllegalStateException("File already exists: ${newFile.absolutePath}"))
        return if (oldFile.renameTo(newFile)) {
            Result.success(newFile.absolutePath)
        } else {
            Result.failure(IllegalStateException("Failed to rename file"))
        }
    }

    actual fun createFolder(parentPath: String, folderName: String): Result<String> {
        val parent = File(parentPath)
        if (!parent.exists() || !parent.isDirectory) return Result.failure(IllegalStateException("Parent directory not found: $parentPath"))
        val newDir = File(parent, folderName)
        if (newDir.exists()) return Result.failure(IllegalStateException("Folder already exists: ${newDir.absolutePath}"))
        return if (newDir.mkdir()) {
            Result.success(newDir.absolutePath)
        } else {
            Result.failure(IllegalStateException("Failed to create folder"))
        }
    }

    actual fun deleteFile(path: String): Result<Unit> {
        val file = File(path)
        if (!file.exists()) return Result.failure(IllegalStateException("File not found: $path"))
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        return if (deleted) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to delete file"))
        }
    }
}
