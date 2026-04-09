package com.manhwaviewer.presentation.util

import androidx.compose.ui.graphics.ImageBitmap
expect fun ByteArray.toImageBitmap(): ImageBitmap

expect suspend fun getImageDimensions(bytes: ByteArray): Pair<Int, Int>

expect suspend fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray

expect suspend fun mergeImagesHorizontally(first: ByteArray, second: ByteArray): ByteArray
