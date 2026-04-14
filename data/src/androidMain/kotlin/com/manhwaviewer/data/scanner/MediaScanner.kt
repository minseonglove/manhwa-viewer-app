package com.manhwaviewer.data.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.datetime.Instant
import java.io.File

actual class MediaScanner(private val context: Context) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val archiveExtensions = setOf("zip", "cbz", "cbr")

    actual fun scanDirectory(directoryPath: String): List<ArchiveFile> {
        val dir = try {
            File(directoryPath).canonicalFile
        } catch (e: Exception) {
            return emptyList()
        }
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val results = mutableListOf<ArchiveFile>()

        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> {
                    val imageCount = countImages(file)
                    results.add(
                        ArchiveFile(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            type = ArchiveType.FOLDER,
                            totalPages = imageCount,
                            addedAt = Instant.fromEpochMilliseconds(file.lastModified()),
                            hasImages = imageCount > 0,
                        )
                    )
                }
                file.isFile && file.extension.lowercase() in archiveExtensions -> {
                    val ext = file.extension.lowercase()
                    results.add(
                        ArchiveFile(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            type = when (ext) {
                                "cbz" -> ArchiveType.CBZ
                                "cbr" -> ArchiveType.CBR
                                else -> ArchiveType.ZIP
                            },
                            addedAt = Instant.fromEpochMilliseconds(file.lastModified()),
                            hasImages = false,
                        )
                    )
                }
                file.isFile && file.extension.lowercase() in imageExtensions -> {
                    results.add(
                        ArchiveFile(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            type = ArchiveType.IMAGE,
                            addedAt = Instant.fromEpochMilliseconds(file.lastModified()),
                            hasImages = false,
                        )
                    )
                }
            }
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

    private fun countImages(folder: File): Int =
        folder.listFiles()?.count { it.isFile && it.extension.lowercase() in imageExtensions } ?: 0
}
