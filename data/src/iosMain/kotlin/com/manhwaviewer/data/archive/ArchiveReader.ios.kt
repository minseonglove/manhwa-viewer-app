package com.manhwaviewer.data.archive

import cnames.structs.archive_entry
import kotlinx.cinterop.*
import libarchive.*
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.timeIntervalSince1970
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
                            val typedBuf = buf.reinterpret<ByteVar>()
                            var totalRead = 0L
                            while (totalRead < size) {
                                val r = archive_read_data(a, typedBuf + totalRead, (size - totalRead).toULong())
                                if (r <= 0) break
                                totalRead += r
                            }
                            result = typedBuf.readBytes(totalRead.toInt())
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

    actual fun getImageEntriesSorted(archivePath: String, sortByDate: Boolean, ascending: Boolean): List<String> {
        val fileManager = NSFileManager.defaultManager

        // Check if directory
        memScoped {
            val isDir = alloc<BooleanVar>()
            if (fileManager.fileExistsAtPath(archivePath, isDirectory = isDir.ptr)) {
                if (isDir.value) {
                    return getImagesFromFolderSorted(archivePath, sortByDate, ascending)
                }
            }
        }

        // Archive file: sort by name only (date not available per-entry)
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

                    if (archive_entry_filetype(entry).toInt() != 0x4000 && isImageFile(name)) {
                        entries.add(name)
                    }
                    archive_read_data_skip(a)
                }
            }
        }

        archive_read_free(a)
        val sorted = entries.sorted()
        return if (ascending) sorted else sorted.reversed()
    }

    private fun getImagesFromFolderSorted(folderPath: String, sortByDate: Boolean, ascending: Boolean): List<String> {
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(folderPath, error = null) ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val imageNames = (contents as List<String>)
            .filter { isImageFile(it) }

        val sorted = if (sortByDate) {
            imageNames.sortedBy { name ->
                val attrs = fileManager.attributesOfItemAtPath("$folderPath/$name", error = null)
                val date = attrs?.get(NSFileModificationDate) as? NSDate
                date?.timeIntervalSince1970 ?: 0.0
            }
        } else {
            imageNames.sorted()
        }

        return if (ascending) sorted else sorted.reversed()
    }

    private fun getImagesFromFolder(folderPath: String): List<String> {
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(folderPath, error = null) ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        return (contents as List<String>)
            .filter { isImageFile(it) }
            .sorted()
    }

    private fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in imageExtensions
    }
}
