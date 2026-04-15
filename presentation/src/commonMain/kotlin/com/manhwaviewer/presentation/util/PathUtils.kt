package com.manhwaviewer.presentation.util

fun buildPathSegments(path: String, defaultScanPath: String): List<Pair<String, String>> {
    if (path.startsWith(defaultScanPath)) {
        val result = mutableListOf<Pair<String, String>>("" to defaultScanPath)
        val remaining = path.removePrefix(defaultScanPath)
        val extraParts = remaining.split("/").filter { it.isNotEmpty() }
        var accumulated = defaultScanPath
        for (part in extraParts) {
            accumulated = "$accumulated/$part"
            result.add(part to accumulated)
        }
        return result
    }
    val parts = path.split("/").filter { it.isNotEmpty() }
    val result = mutableListOf<Pair<String, String>>()
    var accumulated = ""
    for (part in parts) {
        accumulated = "$accumulated/$part"
        result.add(part to accumulated)
    }
    return result
}
