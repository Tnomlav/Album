package com.example.album.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFileOpsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writesANewFileAndReportsProgress() {
        val target = File(folder.root, "photo.jpg")
        val progress = mutableListOf<Long>()
        val size = COPY_PROGRESS_STEP_BYTES.toInt() * 2 + 7
        assertTrue(writeFileReplacing(target, ByteArrayInputStream(ByteArray(size)), onProgress = { progress += it }))
        assertEquals(size.toLong(), target.length())
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.last() > 0L)
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test
    fun keepsTheOldFileWhenTheCopyFails() {
        val target = File(folder.root, "photo.jpg")
        target.writeText("old")
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("boom")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw IOException("boom")
        }
        assertFalse(writeFileReplacing(target, failing))
        assertEquals("old", target.readText())
        // No stray temporary file left behind.
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test
    fun keepsTheOldFileWhenTheCopyIsEmpty() {
        val target = File(folder.root, "photo.jpg")
        target.writeText("old")
        assertFalse(writeFileReplacing(target, ByteArrayInputStream(ByteArray(0))))
        assertEquals("old", target.readText())
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test
    fun keepsTheOldFileWhenItCannotBeMovedAside() {
        val target = object : File(folder.root, "photo.jpg") {
            override fun renameTo(dest: File): Boolean = false
        }
        target.writeText("old")
        assertFalse(writeFileReplacing(target, ByteArrayInputStream(ByteArray(16))))
        assertEquals("old", target.readText())
        // The temporary copy is removed; the old target was never touched.
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test
    fun replacesTheOldFileOnlyAfterTheNewOneIsComplete() {
        val target = File(folder.root, "photo.jpg")
        target.writeText("old")
        val payload = ByteArray(1024) { it.toByte() }
        assertTrue(writeFileReplacing(target, ByteArrayInputStream(payload)))
        assertEquals(payload.size.toLong(), target.length())
        assertTrue(target.readBytes().contentEquals(payload))
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test
    fun appliesTheTimestampWhenAsked() {
        val target = File(folder.root, "photo.jpg")
        val stamp = 1_600_000_000_000L
        assertTrue(writeFileReplacing(target, ByteArrayInputStream(ByteArray(16)), timestampMs = stamp))
        assertEquals(stamp, target.lastModified())
    }
}
