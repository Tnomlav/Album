package com.example.album.data

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Last 20 file-operation outcomes, for the diagnostics export.
 *
 * Deliberately records only the operation kind, whether it succeeded and a
 * short reason (conflict policy, failure enum). File names, folders and URIs
 * are never stored here: the diagnostics report must stay safe to share.
 */
object FileOperationLog {
    enum class Kind(val label: String) {
        Transfer("transfer"),
        Delete("delete"),
        Rename("rename"),
        CreateFolder("create-folder")
    }

    data class Entry(
        val at: Long,
        val kind: Kind,
        val success: Boolean,
        val detail: String
    )

    private const val CAPACITY = 20
    private val entries = ArrayDeque<Entry>(CAPACITY)

    @Synchronized
    fun record(kind: Kind, success: Boolean, detail: String = "") {
        if (entries.size == CAPACITY) entries.removeFirst()
        entries.addLast(Entry(System.currentTimeMillis(), kind, success, detail))
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    fun format(entry: Entry): String {
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(entry.at))
        val result = if (entry.success) "ok" else "failed"
        return if (entry.detail.isBlank()) "$time ${entry.kind.label} $result"
        else "$time ${entry.kind.label} $result (${entry.detail})"
    }
}
