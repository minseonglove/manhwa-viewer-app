package com.manhwaviewer.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [ArchiveFileEntity::class],
    version = 3,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun archiveFileDao(): ArchiveFileDao
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

// Room 컴파일러가 actual을 자동 생성
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
