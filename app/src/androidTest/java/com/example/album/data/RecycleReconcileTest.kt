package com.example.album.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M-16: startup reconciliation must delete files with no metadata row and drop
 * rows whose private backup disappeared.
 */
@RunWith(AndroidJUnit4::class)
class RecycleReconcileTest {
    @Test
    fun reconcileDropsOrphansAndMissingRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "recycle_bin").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }

        val kept = File(directory, "kept.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val orphan = File(directory, "orphan.bin").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val missing = File(directory, "missing.bin")

        val entries = JSONArray()
            .put(entry(id = "kept", storedPath = kept.absolutePath))
            .put(entry(id = "missing", storedPath = missing.absolutePath))
        val preferences = context.getSharedPreferences("cleanup_preferences", Context.MODE_PRIVATE)
        preferences.edit().putString("recycle_entries", entries.toString()).commit()

        val removed = CleanupRepository(context).reconcileRecycleBin()

        assertEquals(1, removed)
        assertFalse(orphan.exists())
        assertTrue(kept.exists())
        val remaining = preferences.getString("recycle_entries", "[]") ?: "[]"
        assertTrue(remaining.contains("kept"))
        assertFalse(remaining.contains("missing"))
    }

    private fun entry(id: String, storedPath: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceUri", "content://media/external/images/media/$id")
        put("storedPath", storedPath)
        put("originalName", "$id.jpg")
        put("originalFolder", "Pictures")
        put("mimeType", "image/jpeg")
        put("dateTaken", 0L)
        put("dateModified", 0L)
        put("duration", 0L)
        put("isVideo", false)
        put("deletedAt", 0L)
        put("systemTrashed", false)
    }
}
