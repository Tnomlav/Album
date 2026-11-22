package com.example.album.data

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Last known media lists. MediaStore queries can take a while on large
 * libraries, so the app shows this snapshot immediately and replaces it with
 * the fresh scan results.
 */
object MediaSnapshotStore {
    private const val FILE_NAME = "media_snapshot.json"

    /**
     * Bumped whenever the JSON shape or its meaning changes. A snapshot written
     * by a newer version is ignored instead of being misread, and the app falls
     * back to a full scan. Older versions are read as-is (the fields are
     * additive), so upgrading does not lose the instant first paint.
     */
    internal const val SCHEMA_VERSION = 2

    data class Snapshot(
        val images: List<MediaItem>,
        val videos: List<MediaItem>,
        val localImages: List<MediaItem>,
        val localVideos: List<MediaItem>,
        /** Wall clock of the scan that produced this snapshot, in epoch millis. */
        val savedAt: Long = 0L
    )

    // Last snapshot parsed or written in this process. Warm starts (returning to
    // the grid, configuration changes, tab switches) read this instead of
    // parsing a multi-megabyte JSON file on the main thread, which is what used
    // to stall the first frame of a cold start.
    @Volatile
    private var inMemory: Snapshot? = null

    /** The snapshot already parsed in this process, without touching the disk. */
    fun cached(): Snapshot? = inMemory

    fun save(context: Context, snapshot: Snapshot) {
        inMemory = snapshot
        runCatching {
            writeAtomically(File(context.filesDir, FILE_NAME)) { json ->
                json.beginObject()
                json.name("version").value(SCHEMA_VERSION)
                json.name("savedAt").value(snapshot.savedAt)
                json.name("images").beginArray()
                writeItems(json, snapshot.images)
                json.endArray()
                json.name("videos").beginArray()
                writeItems(json, snapshot.videos)
                json.endArray()
                json.name("localImages").beginArray()
                writeItems(json, snapshot.localImages)
                json.endArray()
                json.name("localVideos").beginArray()
                writeItems(json, snapshot.localVideos)
                json.endArray()
                json.endObject()
            }
        }
    }

    fun load(context: Context): Snapshot? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) {
            inMemory = null
            return@runCatching null
        }
        val root = JSONObject(file.readText())
        if (!snapshotIsReadable(root.optInt("version", 0))) return@runCatching null
        Snapshot(
            images = decode(root.optJSONArray("images")),
            videos = decode(root.optJSONArray("videos")),
            localImages = decode(root.optJSONArray("localImages")),
            localVideos = decode(root.optJSONArray("localVideos")),
            savedAt = root.optLong("savedAt", 0L)
        ).takeIf { it.images.isNotEmpty() || it.videos.isNotEmpty() || it.localImages.isNotEmpty() || it.localVideos.isNotEmpty() }
    }.getOrNull().also { inMemory = it }

    /**
     * Writes through a temporary file and fsyncs it before the rename, so a
     * process kill or a full disk cannot leave a half-written snapshot behind.
     */
    internal fun writeAtomically(file: File, contents: String) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        java.io.FileOutputStream(temporary).use { output ->
            output.write(contents.toByteArray())
            output.flush()
            output.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    /**
     * Streams the snapshot instead of building an in-memory JSONObject tree.
     * On a 49k-item library the old path held the object tree and the final
     * string at the same time; this writes straight to the temp file.
     */
    private fun writeAtomically(file: File, write: (JsonWriter) -> Unit) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        val output = FileOutputStream(temporary)
        val json = JsonWriter(OutputStreamWriter(output, Charsets.UTF_8))
        try {
            write(json)
            json.flush()
            output.fd.sync()
        } finally {
            runCatching { json.close() }
            runCatching { output.close() }
        }
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun writeItems(json: JsonWriter, items: List<MediaItem>) {
        items.forEach { item ->
            json.beginObject()
            json.name("id").value(item.id)
            json.name("uri").value(item.uri.toString())
            json.name("name").value(item.name)
            json.name("folder").value(item.folder)
            json.name("dateTaken").value(item.dateTaken)
            json.name("mimeType").value(item.mimeType)
            json.name("relativePath").value(item.relativePath.orEmpty())
            json.name("size").value(item.size)
            json.name("dateModified").value(item.dateModified)
            json.name("duration").value(item.duration)
            json.name("width").value(item.width)
            json.name("height").value(item.height)
            json.name("isVideo").value(item.isVideo)
            json.name("isDocument").value(item.isDocument)
            json.endObject()
        }
    }

    private fun encode(items: List<MediaItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("uri", item.uri.toString())
                    .put("name", item.name)
                    .put("folder", item.folder)
                    .put("dateTaken", item.dateTaken)
                    .put("mimeType", item.mimeType)
                    .put("relativePath", item.relativePath.orEmpty())
                    .put("size", item.size)
                    .put("dateModified", item.dateModified)
                    .put("duration", item.duration)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("isVideo", item.isVideo)
                    .put("isDocument", item.isDocument)
            )
        }
        return array
    }

    /** A snapshot written by a newer app version must not be read as if it were ours. */
    internal fun snapshotIsReadable(version: Int): Boolean = version <= SCHEMA_VERSION

    private fun decode(array: JSONArray?): List<MediaItem> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = entry.optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return@mapNotNull null
            MediaItem(
                id = entry.optLong("id"),
                uri = uri,
                name = entry.optString("name"),
                folder = entry.optString("folder"),
                dateTaken = entry.optLong("dateTaken"),
                mimeType = entry.optString("mimeType"),
                relativePath = entry.optString("relativePath").takeIf { it.isNotBlank() },
                size = entry.optLong("size"),
                dateModified = entry.optLong("dateModified"),
                duration = entry.optLong("duration"),
                width = entry.optInt("width"),
                height = entry.optInt("height"),
                isVideo = entry.optBoolean("isVideo"),
                isDocument = entry.optBoolean("isDocument")
            )
        }
    }
}
