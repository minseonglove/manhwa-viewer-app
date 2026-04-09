package com.manhwaviewer.presentation.coil

import coil3.PlatformContext
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath

actual fun newImageDiskCache(context: PlatformContext): DiskCache {
    return DiskCache.Builder()
        .directory(context.cacheDir.resolve("coil_disk_cache").toOkioPath())
        .maxSizePercent(0.02)
        .build()
}
