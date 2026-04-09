package com.manhwaviewer.presentation.coil

import coil3.PlatformContext
import coil3.disk.DiskCache

expect fun newImageDiskCache(context: PlatformContext): DiskCache
