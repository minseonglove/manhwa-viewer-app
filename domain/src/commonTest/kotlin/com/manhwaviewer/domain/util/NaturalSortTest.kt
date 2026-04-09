package com.manhwaviewer.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals

class NaturalSortTest {

    @Test
    fun sortsNumbersNaturally() {
        val input = listOf("vol2", "vol10", "vol1")
        val sorted = input.sortedBy { naturalSortKey(it) }
        assertEquals(listOf("vol1", "vol2", "vol10"), sorted)
    }

    @Test
    fun handlesNoNumbers() {
        val input = listOf("banana", "apple", "cherry")
        val sorted = input.sortedBy { naturalSortKey(it) }
        assertEquals(listOf("apple", "banana", "cherry"), sorted)
    }

    @Test
    fun handlesMixedCase() {
        val input = listOf("Vol2", "vol10", "VOL1")
        val sorted = input.sortedBy { naturalSortKey(it) }
        assertEquals(listOf("VOL1", "Vol2", "vol10"), sorted)
    }

    @Test
    fun handlesLeadingZeros() {
        val input = listOf("ch003", "ch01", "ch002")
        val sorted = input.sortedBy { naturalSortKey(it) }
        assertEquals(listOf("ch01", "ch002", "ch003"), sorted)
    }

    @Test
    fun handlesMultipleNumbers() {
        val input = listOf("s2e10", "s2e2", "s1e10")
        val sorted = input.sortedBy { naturalSortKey(it) }
        assertEquals(listOf("s1e10", "s2e2", "s2e10"), sorted)
    }
}
