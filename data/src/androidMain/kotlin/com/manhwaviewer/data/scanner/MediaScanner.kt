package com.manhwaviewer.data.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import com.manhwaviewer.domain.entity.ArchiveFile
import com.manhwaviewer.domain.entity.ArchiveType
import kotlinx.datetime.Clock
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
        val now = Clock.System.now()

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
                            addedAt = now,
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
                            addedAt = now,
                            hasImages = true,
                        )
                    )
                }
                file.isFile && file.extension.lowercase() in imageExtensions -> {
                    results.add(
                        ArchiveFile(
                            filePath = file.absolutePath,
                            fileName = file.name,
                            type = ArchiveType.IMAGE,
                            addedAt = now,
                            hasImages = true,
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

    private fun countImages(folder: File): Int =
        folder.listFiles()?.count { it.isFile && it.extension.lowercase() in imageExtensions } ?: 0
}
