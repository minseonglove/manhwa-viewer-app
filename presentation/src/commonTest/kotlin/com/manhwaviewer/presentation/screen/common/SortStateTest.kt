package com.manhwaviewer.presentation.screen.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SortStateTest {

    @Test
    fun `SortState default values are NAME ascending LIST`() {
        val state = SortState()
        assertEquals(SortField.NAME, state.field)
        assertTrue(state.ascending)
        assertEquals(ListViewMode.LIST, state.viewMode)
    }

    @Test
    fun `SortField has NAME and DATE values`() {
        val fields = SortField.entries
        assertTrue(fields.contains(SortField.NAME))
        assertTrue(fields.contains(SortField.DATE))
        assertEquals(2, fields.size)
    }

    @Test
    fun `ListViewMode has LIST and GRID values`() {
        val modes = ListViewMode.entries
        assertTrue(modes.contains(ListViewMode.LIST))
        assertTrue(modes.contains(ListViewMode.GRID))
        assertEquals(2, modes.size)
    }

    @Test
    fun `SortState copy updates field`() {
        val state = SortState()
        val updated = state.copy(field = SortField.DATE)
        assertEquals(SortField.DATE, updated.field)
        assertTrue(updated.ascending)
    }

    @Test
    fun `SortState copy toggles ascending`() {
        val state = SortState()
        val updated = state.copy(ascending = false)
        assertEquals(false, updated.ascending)
    }

    @Test
    fun `SortState copy changes viewMode`() {
        val state = SortState()
        val updated = state.copy(viewMode = ListViewMode.GRID)
        assertEquals(ListViewMode.GRID, updated.viewMode)
    }
}
