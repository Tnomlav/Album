package com.example.album.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaSnapshotStoreFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `atomic write creates the file with the given contents`() {
        val file = File(temporaryFolder.root, "snapshot.json")
        MediaSnapshotStore.writeAtomically(file, "hello")
        assertEquals("hello", file.readText())
    }

    @Test
    fun `atomic write replaces an existing file`() {
        val file = File(temporaryFolder.root, "snapshot.json")
        file.writeText("old")
        MediaSnapshotStore.writeAtomically(file, "new")
        assertEquals("new", file.readText())
    }

    @Test
    fun `atomic write leaves no temporary file behind`() {
        val file = File(temporaryFolder.root, "snapshot.json")
        MediaSnapshotStore.writeAtomically(file, "content")
        val leftovers = temporaryFolder.root.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue(leftovers.isEmpty())
    }

    @Test
    fun `a newer schema version is not readable`() {
        assertFalse(MediaSnapshotStore.snapshotIsReadable(MediaSnapshotStore.SCHEMA_VERSION + 1))
    }

    @Test
    fun `current and legacy schema versions stay readable`() {
        assertTrue(MediaSnapshotStore.snapshotIsReadable(MediaSnapshotStore.SCHEMA_VERSION))
        // 0 is what a snapshot from before the version field looks like.
        assertTrue(MediaSnapshotStore.snapshotIsReadable(0))
    }
}
