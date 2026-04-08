package com.manhwaviewer.domain.util

fun naturalSortKey(name: String): String {
    return name.lowercase().replace(Regex("(\\d+)")) { match ->
        match.value.padStart(20, '0')
    }
}
