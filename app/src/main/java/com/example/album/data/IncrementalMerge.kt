package com.example.album.data

/**
 * Outcome of applying a delta to a list, plus whether it can be trusted as the
 * complete list.
 *
 * @property items the merged list, in insertion order (existing rows first).
 * @property complete true when [items] covers exactly the alive keys. A missing
 * row means the delta was incomplete (a new row with no usable timestamp, an
 * edit the selection missed), and the caller must do a full scan instead of
 * showing a library that silently lost an item.
 */
data class IncrementalMergeResult<T>(
    val items: List<T>,
    val complete: Boolean
)

/**
 * Applies changed rows to [current] and drops rows that no longer exist.
 *
 * Kept generic and free of Android types so it can be unit tested: the caller
 * supplies [keyOf] (a MediaItem's URI in the app).
 */
fun <T> mergeChangedItems(
    current: List<T>,
    changed: List<T>,
    aliveKeys: Set<String>,
    keyOf: (T) -> String
): IncrementalMergeResult<T> {
    val merged = LinkedHashMap<String, T>(current.size + changed.size)
    current.forEach { merged[keyOf(it)] = it }
    changed.forEach { merged[keyOf(it)] = it }
    // A row that is gone from the store must not linger until the next full
    // scan; a row whose key is not in the delta but still alive stays as it was.
    merged.keys.retainAll(aliveKeys)
    return IncrementalMergeResult(merged.values.toList(), merged.size == aliveKeys.size)
}
