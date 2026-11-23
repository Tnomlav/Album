package com.example.album.data

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory SAF provider used by instrumentation tests.
 *
 * The production overwrite path had only transaction-level fakes before; this
 * provider lets the test drive the real ContentResolver + DocumentsContract
 * path and make rename/delete fail on demand (T2).
 */
class FakeDocumentsProvider : DocumentsProvider() {
    data class Doc(
        val id: String,
        val name: String,
        val mimeType: String,
        val parentId: String?,
        val isDirectory: Boolean,
        var bytes: ByteArray = ByteArray(0)
    )

    private val documents = ConcurrentHashMap<String, Doc>()
    private val nextId = AtomicInteger(1)

    @Volatile var failRename = false
    @Volatile var failDelete = false

    override fun onCreate(): Boolean {
        reset()
        return true
    }

    fun reset(failRename: Boolean = false, failDelete: Boolean = false) {
        documents.clear()
        this.failRename = failRename
        this.failDelete = failDelete
        documents["root"] = Doc("root", "root", Document.MIME_TYPE_DIR, null, true)
        documents["dest"] = Doc("dest", "dest", Document.MIME_TYPE_DIR, "root", true)
        documents["src"] = Doc("src", "src.jpg", "image/jpeg", "root", false, "new".toByteArray())
        documents["dest-src"] = Doc("dest-src", "src.jpg", "image/jpeg", "dest", false, "old".toByteArray())
    }

    fun exists(id: String): Boolean = documents.containsKey(id)

    fun text(id: String): String? = documents[id]?.bytes?.toString(Charsets.UTF_8)

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        cursor.addRow(
            arrayOf(
                "root",
                "fake",
                "Fake storage",
                Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_RENAME,
                "root",
                "root"
            ).take(cursor.columnCount)
        )
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        documents[documentId]?.let { cursor.addRow(rowFor(it, cursor)) }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        documents.values
            .filter { it.parentId == parentDocumentId }
            .forEach { cursor.addRow(rowFor(it, cursor)) }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val doc = documents[documentId] ?: throw IllegalArgumentException("missing $documentId")
        val file = File(context!!.cacheDir, "fake-doc-${doc.id}")
        if (mode.contains('w')) {
            file.writeBytes(ByteArray(0))
        } else {
            file.writeBytes(doc.bytes)
        }
        val flags = when {
            mode.contains('w') && mode.contains('r') -> ParcelFileDescriptor.MODE_READ_WRITE
            mode.contains('w') -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        val descriptor = ParcelFileDescriptor.open(file, flags)
        if (mode.contains('w')) {
            // Keep the in-memory bytes in sync for the assertions after writes.
            doc.bytes = file.readBytes()
        }
        return descriptor
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val id = "new-${nextId.getAndIncrement()}"
        documents[id] = Doc(
            id = id,
            name = displayName,
            mimeType = mimeType,
            parentId = parentDocumentId,
            isDirectory = false
        )
        return DocumentsContract.buildDocumentUri(AUTHORITY, id).toString()
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        if (failRename) return null
        val doc = documents[documentId] ?: return null
        documents[documentId] = doc.copy(name = displayName)
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentId).toString()
    }

    override fun deleteDocument(documentId: String) {
        if (failDelete) throw IllegalStateException("delete refused")
        if (documents[documentId]?.isDirectory == true) {
            documents.values.removeIf { it.parentId == documentId }
        }
        documents.remove(documentId)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            "reset" -> {
                reset(
                    failRename = extras?.getBoolean("failRename") ?: false,
                    failDelete = extras?.getBoolean("failDelete") ?: false
                )
                Bundle.EMPTY
            }
            "exists" -> Bundle().apply { putBoolean("value", exists(arg.orEmpty())) }
            "text" -> Bundle().apply { putString("value", text(arg.orEmpty())) }
            else -> super.call(method, arg, extras)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        var current = documents[documentId]
        while (current?.parentId != null) {
            if (current.parentId == parentDocumentId) return true
            current = documents[current.parentId]
        }
        return false
    }

    private fun rowFor(doc: Doc, cursor: MatrixCursor): Array<Any?> {
        val values = mapOf(
            Document.COLUMN_DOCUMENT_ID to doc.id,
            Document.COLUMN_DISPLAY_NAME to doc.name,
            Document.COLUMN_MIME_TYPE to doc.mimeType,
            Document.COLUMN_FLAGS to flags(doc),
            Document.COLUMN_SIZE to doc.bytes.size.toLong(),
            Document.COLUMN_LAST_MODIFIED to 0L
        )
        return Array(cursor.columnCount) { index ->
            values[cursor.getColumnName(index)]
        }
    }

    private fun flags(doc: Doc): Int = if (doc.isDirectory) {
        Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_RENAME or
            Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_WRITE
    } else {
        Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_RENAME or
            Document.FLAG_SUPPORTS_DELETE
    }

    companion object {
        const val AUTHORITY = "com.example.album.test.docs"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_SUMMARY
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )
    }
}
