package com.manhwaviewer.domain.entity

import kotlinx.datetime.Instant

data class VaultFile(
    val id: Long = 0,
    val relativePath: String,
    val originalPath: String,
    val addedAt: Instant,
)
