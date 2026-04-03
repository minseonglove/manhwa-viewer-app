package com.manhwaviewer.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [ArchiveFileEntity::class, TranslationCacheEntity::class, PageTranslationCacheEntity::class],
    version = 6,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun archiveFileDao(): ArchiveFileDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun pageTranslationCacheDao(): PageTranslationCacheDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE archive_files ADD COLUMN hasImages INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_archive_files_lastReadAt ON archive_files(lastReadAt)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_archive_files_hasImages ON archive_files(hasImages)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_archive_files_filePath ON archive_files(filePath)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS translation_cache (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "sourceText TEXT NOT NULL, " +
                "translatedText TEXT NOT NULL, " +
                "sourceLanguage TEXT NOT NULL, " +
                "targetLanguage TEXT NOT NULL, " +
                "engine TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_translation_cache_sourceText_sourceLanguage_targetLanguage " +
                "ON translation_cache(sourceText, sourceLanguage, targetLanguage)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS page_translation_cache (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "archiveId INTEGER NOT NULL, " +
                "pageIndicesJson TEXT NOT NULL, " +
                "viewMode TEXT NOT NULL, " +
                "readingDirection TEXT NOT NULL, " +
                "imageWidth INTEGER NOT NULL, " +
                "imageHeight INTEGER NOT NULL, " +
                "blocksJson TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_page_translation_cache_archiveId_pageIndicesJson_viewMode " +
                "ON page_translation_cache(archiveId, pageIndicesJson, viewMode)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS page_translation_cache")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS page_translation_cache (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "archiveId INTEGER NOT NULL, " +
                "page INTEGER NOT NULL, " +
                "imageWidth INTEGER NOT NULL, " +
                "imageHeight INTEGER NOT NULL, " +
                "blocksJson TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_page_translation_cache_archiveId_page " +
                "ON page_translation_cache(archiveId, page)"
        )
    }
}

// Room 컴파일러가 actual을 자동 생성
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
