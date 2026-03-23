package com.manhwaviewer.android

import android.app.Application
import com.manhwaviewer.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ManhwaViewerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ManhwaViewerApp)
            modules(appModule)
        }
    }
}
