package com.manhwaviewer.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.manhwaviewer.data.di.commonDataModule
import com.manhwaviewer.ios.di.iosPlatformModule
import com.manhwaviewer.presentation.App
import com.manhwaviewer.presentation.di.sharedModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(iosPlatformModule, commonDataModule, sharedModule)
    }
}

fun MainViewController() = ComposeUIViewController { App() }
