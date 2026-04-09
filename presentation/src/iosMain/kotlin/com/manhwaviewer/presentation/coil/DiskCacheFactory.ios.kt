package com.manhwaviewer.presentation.coil

import coil3.PlatformContext
import coil3.disk.DiskCache
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun newImageDiskCache(context: PlatformContext): DiskCache {
    val cacheDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory, NSUserDomainMask, true,
    ).first() as String
    return DiskCache.Builder()
        .directory("$cacheDir/coil_disk_cache".toPath())
        .maxSizePercent(0.02)
        .build()
}
