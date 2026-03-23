package com.manhwaviewer.presentation.screen.common

enum class SortField {
    NAME, DATE
}

enum class ListViewMode {
    LIST, GRID
}

data class SortState(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true,
    val viewMode: ListViewMode = ListViewMode.LIST,
)
