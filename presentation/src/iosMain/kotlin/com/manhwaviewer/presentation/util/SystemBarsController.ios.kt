package com.manhwaviewer.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

@Composable
actual fun SystemBarsVisibility(visible: Boolean) {
    DisposableEffect(visible) {
        UIApplication.sharedApplication.setStatusBarHidden(!visible, animated = true)

        onDispose {
            UIApplication.sharedApplication.setStatusBarHidden(false, animated = false)
        }
    }
}
