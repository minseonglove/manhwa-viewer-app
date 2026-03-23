package com.manhwaviewer.data.archive

import java.io.File

actual class ArchiveReader actual constructor() {
    private val bridge = LibArchiveBridge()
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    actual fun getImageEntries(archivePath: String): List<String> {
        val file = File(archivePath)
        return when {
            file.isDirectory -> getImagesFromFolder(file)
            file.isFile -> bridge.nativeGetImageEntries(archivePath).toList()
            else -> emptyList()
        }
    }

    actual fun getImageBytes(archivePath: String, entryName: String): ByteArray? {
        // Path traversal guard
        if (entryName.contains("..") || entryName.startsWith("/")) return null

        val file = File(archivePath)
        return when {
            file.isDirectory -> {
                val target = File(file, entryName)
                if (!target.canonicalPath.startsWith(file.canonicalPath + File.separator)) return null
                target.takeIf { it.exists() && it.isFile }?.readBytes()
            }
            file.isFile -> bridge.nativeGetImageBytes(archivePath, entryName)
            else -> null
        }
    }

    actual fun getPageCount(archivePath: String): Int = getImageEntries(archivePath).size

    actual fun close() {
        // No persistent native resources to clean up
    }

    private fun getImagesFromFolder(folder: File): List<String> =
        folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            ?.sortedBy { it.name }
            ?.map { it.name }
            ?: emptyList()
}
