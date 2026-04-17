package com.manhwaviewer.presentation.util

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

actual val ioDispatcher: CoroutineContext = Dispatchers.Default
