package com.example.album.data

import android.content.Context
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T2: overwrite through a real ContentResolver + DocumentsProvider.
 *
 * The provider refuses to rename the old target aside. The transfer must fail
 * without deleting the old destination or the source file.
 */
@RunWith(AndroidJUnit4::class)
class SafOverwriteFailureTest {
    @Test
    fun failedRenameKeepsOldTargetAndSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUri(FakeDocumentsProvider.AUTHORITY, "root")

        resolver.call(root, "reset", null, Bundle().apply { putBoolean("failRename", true) })

        val tree = DocumentsContract.buildTreeDocumentUri(FakeDocumentsProvider.AUTHORITY, "root")
        context.getSharedPreferences("local_folder_preferences", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("tree_uris", setOf(tree.toString()))
            .commit()

        val source = MediaItem(
            id = 1L,
            uri = DocumentsContract.buildDocumentUri(FakeDocumentsProvider.AUTHORITY, "src"),
            name = "src.jpg",
            folder = "root",
            dateTaken = 1L,
            mimeType = "image/jpeg",
            size = 3L,
            dateModified = 1L,
            isDocument = true
        )

        val result = MediaRepository(context).transfer(
            item = source,
            destinationFolder = "dest",
            conflictPolicy = ConflictPolicy.Overwrite,
            preserveModifiedDate = true,
            mode = TransferMode.Move
        )

        assertFalse(result.success)
        assertEquals(
            "old",
            resolver.call(root, "text", "dest-src", null)?.getString("value")
        )
        assertTrue(
            resolver.call(root, "exists", "src", null)?.getBoolean("value") == true
        )
    }
}
