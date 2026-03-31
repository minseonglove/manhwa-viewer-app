package com.manhwaviewer.presentation.util

import androidx.compose.ui.graphics.ImageBitmap

expect fun ByteArray.toImageBitmap(): ImageBitmap

expect fun getImageDimensions(bytes: ByteArray): Pair<Int, Int>

expect fun cropImageBytes(bytes: ByteArray, x: Int, y: Int, width: Int, height: Int): ByteArray
