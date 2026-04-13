package com.manhwaviewer.presentation.util

import androidx.compose.runtime.Composable

@Composable
actual fun SystemBarsVisibility(visible: Boolean) {
    // iOS에서는 상태바 제어가 UIViewController.prefersStatusBarHidden 으로 관리됨.
    // ComposeUIViewController에서 직접 제어할 수 없으므로 현재는 no-op.
    // TODO: ComposeUIViewController 래퍼에서 prefersStatusBarHidden override 구현
}
