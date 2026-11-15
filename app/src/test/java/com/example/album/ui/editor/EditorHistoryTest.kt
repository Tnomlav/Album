package com.example.album.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    @Test
    fun `a fresh history can neither undo nor redo`() {
        val history = EditorHistory<Int>()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(1))
        assertNull(history.redo(1))
    }

    @Test
    fun `undo returns the recorded state and enables redo`() {
        val history = EditorHistory<Int>()
        history.record(1)
        assertEquals(1, history.undo(2))
        assertTrue(history.canRedo)
    }

    @Test
    fun `redo returns the state it was undone from`() {
        val history = EditorHistory<Int>()
        history.record(1)
        history.undo(2)
        assertEquals(2, history.redo(1))
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `a new change drops the redo branch`() {
        val history = EditorHistory<Int>()
        history.record(1)
        history.undo(2)
        history.record(3)
        assertFalse(history.canRedo)
        assertEquals(3, history.undo(4))
    }

    @Test
    fun `the stack keeps only the newest entries`() {
        val history = EditorHistory<Int>(limit = 3)
        (1..5).forEach(history::record)
        assertEquals(5, history.undo(6))
        assertEquals(4, history.undo(5))
        assertEquals(3, history.undo(4))
        assertFalse(history.canUndo)
    }

    @Test
    fun `undo and redo walk several steps in both directions`() {
        val history = EditorHistory<Int>()
        history.record(1)
        history.record(2)
        assertEquals(2, history.undo(3))
        assertEquals(1, history.undo(2))
        assertEquals(2, history.redo(1))
        assertEquals(3, history.redo(2))
    }
}
