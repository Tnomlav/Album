package com.example.album.data

/**
 * Folder identity and display names.
 *
 * `MediaItem.folder` is the MediaStore bucket's *leaf* name, so
 * `DCIM/Camera` and `Pictures/Camera` both looked like one "Camera" folder and
 * the Videos page merged their contents. The full path is available in
 * `relativePath`; this file uses it to tell those folders apart and, only when
 * two different folders share a leaf name, shows the full path instead.
 */
fun MediaItem.folderKey(): String = transferFolderPath(relativePath, folder)

/** Leaf name used when the folder name is unambiguous. */
private fun MediaItem.folderLeaf(): String {
    val key = folderKey()
    return key.substringAfterLast('/').ifBlank { key }
}

/**
 * Rewrites [MediaItem.folder] so that folders with the same leaf name in
 * different locations no longer collide.
 *
 * A leaf name that maps to exactly one folder keeps its short name (so the
 * common case looks unchanged); a leaf name shared by several folders is
 * replaced by the full path, e.g. `DCIM/Camera` and `Pictures/Camera`.
 */
fun disambiguateFolderNames(items: List<MediaItem>): List<MediaItem> {
    if (items.isEmpty()) return items
    val keysByLeaf = HashMap<String, MutableSet<String>>(items.size)
    items.forEach { item ->
        keysByLeaf.getOrPut(item.folderLeaf()) { HashSet() }.add(item.folderKey())
    }
    val renamed = HashMap<String, String>(keysByLeaf.size)
    keysByLeaf.forEach { (leaf, keys) ->
        if (keys.size > 1) keys.forEach { key -> renamed[key] = key }
        else keys.forEach { key -> renamed[key] = leaf }
    }
    var changed = false
    val result = items.map { item ->
        val target = renamed[item.folderKey()] ?: item.folder
        if (target == item.folder) {
            item
        } else {
            changed = true
            item.copy(folder = target)
        }
    }
    return if (changed) result else items
}

/**
 * Same rule as [disambiguateFolderNames], applied across several lists at once
 * so an image folder and a video folder with the same path get the same name.
 */
fun disambiguateFolderNames(vararg lists: List<MediaItem>): List<List<MediaItem>> {
    val all = lists.flatMap { it }
    if (all.isEmpty()) return lists.map { it }
    val keysByLeaf = HashMap<String, MutableSet<String>>(all.size)
    all.forEach { item ->
        keysByLeaf.getOrPut(item.folderLeaf()) { HashSet() }.add(item.folderKey())
    }
    val renamed = HashMap<String, String>(keysByLeaf.size)
    keysByLeaf.forEach { (leaf, keys) ->
        if (keys.size > 1) keys.forEach { key -> renamed[key] = key }
        else keys.forEach { key -> renamed[key] = leaf }
    }
    return lists.map { list ->
        var changed = false
        val mapped = list.map { item ->
            val target = renamed[item.folderKey()] ?: item.folder
            if (target == item.folder) item else {
                changed = true
                item.copy(folder = target)
            }
        }
        if (changed) mapped else list
    }
}
