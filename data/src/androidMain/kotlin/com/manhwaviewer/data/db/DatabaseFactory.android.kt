package com.manhwaviewer.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

// Android에서는 Context가 필요하므로 별도 함수 제공
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder(
        context = context.applicationContext,
        klass = AppDatabase::class.java,
        name = context.getDatabasePath("manhwa_viewer.db").absolutePath,
    )

// expect의 actual은 Android에서는 사용하지 않음 (Context가 필요하므로)
// DI에서 직접 getDatabaseBuilder(context)를 호출
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    throw UnsupportedOperationException("Use getDatabaseBuilder(context) on Android")
}
