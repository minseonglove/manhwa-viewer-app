package com.manhwaviewer.data.archive

import kotlinx.cinterop.*
import libarchive.*
import platform.Foundation.NSFileManager
import platform.posix.free
import platform.posix.malloc

actual class ArchiveReader actual constructor() {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    actual fun getImageEntries(archivePath: String): List<String> {
        val fileManager = NSFileManager.defaultManager

        // Check if directory
        memScoped {
            val isDir = alloc<BooleanVar>()
            if (fileManager.fileExistsAtPath(archivePath, isDirectory = isDir.ptr)) {
                if (isDir.value) {
                    return getImagesFromFolder(archivePath)
                }
            }
        }

        // Archive file
        val entries = mutableListOf<String>()
        val a = archive_read_new() ?: return emptyList()

        archive_read_support_format_all(a)
        archive_read_support_filter_all(a)

        if (archive_read_open_filename(a, archivePath, 10240u) == ARCHIVE_OK) {
            memScoped {
                val entryPtr = alloc<CPointerVar<archive_entry>>()
                while (archive_read_next_header(a, entryPtr.ptr) == ARCHIVE_OK) {
                    val entry = entryPtr.value ?: continue
                    val name = archive_entry_pathname(entry)?.toKString() ?: continue

                    if (archive_entry_filetype(entry).toInt() != 0x4000 /* AE_IFDIR */ && isImageFile(name)) {
                        entries.add(name)
                    }
                    archive_read_data_skip(a)
                }
            }
        }

        archive_read_free(a)
        return entries.sorted()
    }

    actual fun getImageBytes(archivePath: String, entryName: String): ByteArray? {
        if (entryName.contains("..") || entryName.startsWith("/")) return null

        val fileManager = NSFileManager.defaultManager

        memScoped {
            val isDir = alloc<BooleanVar>()
            if (fileManager.fileExistsAtPath(archivePath, isDirectory = isDir.ptr) && isDir.value) {
                val filePath = "$archivePath/$entryName"
                val data = fileManager.contentsAtPath(filePath) ?: return null
                return data.bytes?.readBytes(data.length.toInt())
            }
        }

        // Read from archive
        val a = archive_read_new() ?: return null
        archive_read_support_format_all(a)
        archive_read_support_filter_all(a)

        var result: ByteArray? = null

        if (archive_read_open_filename(a, archivePath, 10240u) == ARCHIVE_OK) {
            memScoped {
                val entryPtr = alloc<CPointerVar<archive_entry>>()
                while (archive_read_next_header(a, entryPtr.ptr) == ARCHIVE_OK) {
                    val entry = entryPtr.value ?: continue
                    val name = archive_entry_pathname(entry)?.toKString() ?: continue

                    if (name == entryName) {
                        val size = archive_entry_size(entry)
                        if (size > 0) {
                            val buf = malloc(size.toULong()) ?: break
                            var totalRead = 0L
                            while (totalRead < size) {
                                val r = archive_read_data(a, buf + totalRead, (size - totalRead).toULong())
                                if (r <= 0) break
                                totalRead += r
                            }
                            result = buf.readBytes(totalRead.toInt())
                            free(buf)
                        }
                        break
                    }
                    archive_read_data_skip(a)
                }
            }
        }

        archive_read_free(a)
        return result
    }

    actual fun getPageCount(archivePath: String): Int = getImageEntries(archivePath).size

    actual fun close() {}

    private fun getImagesFromFolder(folderPath: String): List<String> {
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(folderPath, error = null) ?: return emptyList()

        return (0 until contents.count.toInt())
            .mapNotNull { contents.objectAtIndex(it.toULong()) as? String }
            .filter { isImageFile(it) }
            .sorted()
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in imageExtensions
    }
}
