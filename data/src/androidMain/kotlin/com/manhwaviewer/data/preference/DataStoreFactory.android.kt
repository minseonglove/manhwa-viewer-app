package com.manhwaviewer.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createPlatformDataStore(context: Context): DataStore<Preferences> =
    createDataStore(
        producePath = {
            context.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath
        }
    )

// expect의 actual - Android에서는 Context 필요하므로 DI에서 위 함수 사용
actual fun createPlatformDataStore(): DataStore<Preferences> {
    throw UnsupportedOperationException("Use createPlatformDataStore(context) on Android")
}
