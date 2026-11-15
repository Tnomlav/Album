package com.example.album.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Undo/redo ring for the image editor.
 *
 * The editor kept two lists and the push/pop rules inline in a 3500-line
 * composable: every call site had to know to clear redo, and the 60-entry cap
 * was repeated three times. The rules now live here, and [canUndo] / [canRedo]
 * are the only thing the toolbar reads.
 */
class EditorHistory<T>(private val limit: Int = DEFAULT_LIMIT) {
    var undoStack by mutableStateOf<List<T>>(emptyList())
        private set
    var redoStack by mutableStateOf<List<T>>(emptyList())
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Records a state before a change; a new change drops the redo branch. */
    fun record(entry: T) {
        undoStack = (undoStack + entry).takeLast(limit)
        redoStack = emptyList()
    }

    /** Moves one step back, pushing [current] onto the redo stack. */
    fun undo(current: T): T? {
        val previous = undoStack.lastOrNull() ?: return null
        redoStack = (redoStack + current).takeLast(limit)
        undoStack = undoStack.dropLast(1)
        return previous
    }

    /** Moves one step forward, pushing [current] back onto the undo stack. */
    fun redo(current: T): T? {
        val next = redoStack.lastOrNull() ?: return null
        undoStack = (undoStack + current).takeLast(limit)
        redoStack = redoStack.dropLast(1)
        return next
    }

    fun clear() {
        undoStack = emptyList()
        redoStack = emptyList()
    }

    companion object {
        const val DEFAULT_LIMIT = 60
    }
}
