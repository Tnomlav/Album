package com.example.album.data

/**
 * Folder identity and display name.
 *
 * `MediaItem.folder` is the MediaStore bucket's leaf name, so `DCIM/Camera`
 * and `Pictures/Camera` both look like "Camera". The full path in
 * `relativePath` is the real identity: grouping uses [folderKey], while the
 * tile keeps showing the leaf name via [albumDisplayName].
 */
fun MediaItem.folderKey(): String = transferFolderPath(relativePath, folder)

/** The last path segment of a folder key, which is what a tile shows. */
fun albumDisplayName(key: String): String =
    key.trim('/').substringAfterLast('/').ifBlank { key }
