package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalMergeTest {
    private data class Row(val key: String, val value: Int)

    private fun keyOf(row: Row): String = row.key

    @Test
    fun `changed rows replace existing ones`() {
        val current = listOf(Row("a", 1), Row("b", 2))
        val changed = listOf(Row("b", 20))
        val result = mergeChangedItems(current, changed, setOf("a", "b"), ::keyOf)
        assertTrue(result.complete)
        assertEquals(listOf(Row("a", 1), Row("b", 20)), result.items)
    }

    @Test
    fun `new rows are appended`() {
        val current = listOf(Row("a", 1))
        val result = mergeChangedItems(current, listOf(Row("c", 3)), setOf("a", "c"), ::keyOf)
        assertTrue(result.complete)
        assertEquals(setOf("a", "c"), result.items.map(Row::key).toSet())
    }

    @Test
    fun `rows that are no longer alive are dropped`() {
        val current = listOf(Row("a", 1), Row("b", 2))
        val result = mergeChangedItems(current, emptyList(), setOf("a"), ::keyOf)
        assertTrue(result.complete)
        assertEquals(listOf(Row("a", 1)), result.items)
    }

    @Test
    fun `a missing row marks the merge incomplete so the caller rescans`() {
        val current = listOf(Row("a", 1))
        // "b" exists in the store but the delta did not carry it (no usable
        // timestamp), so the merged list is one row short.
        val result = mergeChangedItems(current, emptyList(), setOf("a", "b"), ::keyOf)
        assertFalse(result.complete)
    }

    @Test
    fun `an unchanged library keeps its items`() {
        val current = listOf(Row("a", 1), Row("b", 2))
        val result = mergeChangedItems(current, emptyList(), setOf("a", "b"), ::keyOf)
        assertTrue(result.complete)
        assertEquals(current, result.items)
    }

    @Test
    fun `merge does not mutate the input list`() {
        val current = mutableListOf(Row("a", 1))
        mergeChangedItems(current, listOf(Row("b", 2)), setOf("a", "b"), ::keyOf)
        assertEquals(listOf(Row("a", 1)), current)
    }
}
