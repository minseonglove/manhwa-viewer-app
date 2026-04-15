package com.manhwaviewer.presentation.util

const val VAULT_ROOT_MARKER = "\u0000vault"

fun buildPathSegments(
    path: String,
    defaultScanPath: String,
    vaultPath: String? = null,
): List<Pair<String, String>> {
    if (vaultPath != null && path.startsWith(vaultPath)) {
        val result = mutableListOf<Pair<String, String>>(VAULT_ROOT_MARKER to vaultPath)
        val remaining = path.removePrefix(vaultPath)
        val extraParts = remaining.split("/").filter { it.isNotEmpty() }
        var accumulated = vaultPath
        for (part in extraParts) {
            accumulated = "$accumulated/$part"
            result.add(part to accumulated)
        }
        return result
    }
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
