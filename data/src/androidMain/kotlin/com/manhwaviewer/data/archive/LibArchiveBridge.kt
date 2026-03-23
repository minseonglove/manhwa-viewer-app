package com.manhwaviewer.data.archive

internal class LibArchiveBridge {
    companion object {
        init {
            System.loadLibrary("archive-jni")
        }
    }

    external fun nativeGetImageEntries(archivePath: String): Array<String>
    external fun nativeGetImageBytes(archivePath: String, entryName: String): ByteArray?
}
