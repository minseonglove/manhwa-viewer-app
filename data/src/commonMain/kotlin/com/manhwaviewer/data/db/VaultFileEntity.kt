package com.manhwaviewer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_files",
    indices = [
        Index("relativePath", unique = true),
    ],
)
data class VaultFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val relativePath: String,
    val originalPath: String,
    val addedAt: Long,
)
