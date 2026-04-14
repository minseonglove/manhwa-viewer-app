package com.manhwaviewer.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        ArchiveFileEntity::class,
        TranslationCacheEntity::class,
        PageOcrCacheEntity::class,
        PageOcrTranslationEntity::class,
        VaultFileEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun archiveFileDao(): ArchiveFileDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun pageOcrCacheDao(): PageOcrCacheDao
    abstract fun vaultFileDao(): VaultFileDao
}


// Room 컴파일러가 actual을 자동 생성
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
