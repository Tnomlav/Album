package com.example.album.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The videos next to an externally opened one, so "open with Albumiv" from another
 * app can move to the next video of the same folder.
 *
 * The opener used to build a playlist with this one item, which made the player
 * look like it had no siblings even when the folder was full of videos.
 * Ordering is by file name ascending, which is what file managers show.
 */
suspend fun openExternalVideoPlaylist(context: Context, uri: Uri): List<MediaItem> =
    withContext(Dispatchers.IO) {
        val siblings = videosSharingBucket(context, uri).ifEmpty { videosInFileDirectory(uri) }
        val opened = siblings.firstOrNull { it.uri == uri }
            ?: externalMediaItem(context, uri)
            .takeIf { it.isVideo }
        val playlist = if (opened != null && siblings.none { it.uri == uri }) {
            (siblings + opened).distinctBy { it.uri }
        } else {
            siblings
        }
        playlist.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

/** Reads the opened URI's own metadata; also used when no siblings are found. */
suspend fun externalMediaItem(context: Context, uri: Uri): MediaItem = withContext(Dispatchers.IO) {
    val name = Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/')
    val resolver = context.contentResolver
    val mime = runCatching { resolver.getType(uri).orEmpty() }.getOrDefault("").ifBlank {
        android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            .orEmpty()
    }
    var size = 0L
    var dateModified = 0L
    var relativePath: String? = null
    var folder = "外部打开"
    runCatching {
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        }.toTypedArray()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                fun index(column: String) = cursor.getColumnIndex(column)
                index(MediaStore.MediaColumns.SIZE).takeIf { it >= 0 }?.let { size = cursor.getLong(it) }
                index(MediaStore.MediaColumns.DATE_MODIFIED).takeIf { it >= 0 }?.let {
                    dateModified = cursor.getLong(it)
                }
                index(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME).takeIf { it >= 0 }?.let {
                    cursor.getString(it)?.takeIf(String::isNotBlank)?.let { value -> folder = value }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    index(MediaStore.MediaColumns.RELATIVE_PATH).takeIf { it >= 0 }?.let {
                        relativePath = cursor.getString(it)
                    }
                }
            }
        }
    }
    MediaItem(
        id = uri.toString().hashCode().toLong() and 0xffffffffL,
        uri = uri,
        name = name.ifBlank { "外部媒体" },
        folder = folder,
        dateTaken = 0L,
        mimeType = mime,
        relativePath = relativePath,
        size = size,
        dateModified = dateModified,
        isVideo = mime.startsWith("video/"),
        isDocument = true
    )
}

/** All MediaStore videos in the same bucket, or empty when the URI is not indexed. */
private fun videosSharingBucket(context: Context, uri: Uri): List<MediaItem> {
    val bucketId = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.BUCKET_ID),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L)
    if (bucketId <= 0L) return emptyList()

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val dateColumn = MediaStore.Video.Media.DATE_TAKEN
    val projection = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.DATE_ADDED)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.DURATION)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(dateColumn)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
    }.toTypedArray()

    val items = mutableListOf<MediaItem>()
    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.MediaColumns.BUCKET_ID} = ?",
        arrayOf(bucketId.toString()),
        "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val folderIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val takenIndex = cursor.getColumnIndexOrThrow(dateColumn)
        val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            -1
        }
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val taken = cursor.getLong(takenIndex).takeIf { it > 0 } ?: cursor.getLong(addedIndex) * 1000L
            items += MediaItem(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                name = cursor.getString(nameIndex) ?: "未命名",
                folder = cursor.getString(folderIndex) ?: "其他",
                dateTaken = taken,
                mimeType = cursor.getString(mimeIndex) ?: "video/*",
                relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex) else null,
                size = cursor.getLong(sizeIndex),
                dateModified = cursor.getLong(modifiedIndex),
                duration = cursor.getLong(durationIndex),
                width = cursor.getInt(widthIndex),
                height = cursor.getInt(heightIndex),
                isVideo = true
            )
        }
    }
    return items
}

/** File-manager URIs are often plain file paths: list the directory directly. */
private fun videosInFileDirectory(uri: Uri): List<MediaItem> {
    val file = uri.path?.let(::File)?.takeIf { it.isFile } ?: return emptyList()
    val parent = file.parentFile ?: return emptyList()
    val videos = parent.listFiles()
        .orEmpty()
        .filter { it.isFile && it.isVideoFile() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    return videos.map { sibling ->
        MediaItem(
            id = sibling.absolutePath.hashCode().toLong() and 0xffffffffL,
            uri = Uri.fromFile(sibling),
            name = sibling.name,
            folder = parent.name,
            dateTaken = sibling.lastModified(),
            mimeType = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(sibling.extension.lowercase()) ?: "video/*",
            relativePath = null,
            size = sibling.length(),
            dateModified = sibling.lastModified(),
            isVideo = true,
            isDocument = true
        )
    }
}

private fun File.isVideoFile(): Boolean {
    val extension = extension.lowercase()
    return extension in VIDEO_EXTENSIONS
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "m2ts", "mpg", "mpeg", "flv", "wmv", "asf", "rm", "rmvb", "ogv", "ogm"
)
