package com.example.album.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.album.data.MediaItem
import com.example.album.data.WallpaperQueueState
import com.example.album.data.WallpaperQueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The wallpaper manager queue as one piece of state.
 *
 * The queue used to be three `remember` values plus a save job inside
 * `AlbumApp`, mutated from five places (add, remove, remove-all-of-type, the
 * launcher screen and the restore effect). Every mutation had to remember to
 * call the save function; skipping it meant the queue silently diverged from
 * disk. Here [update] is the only mutation path and it always persists.
 */
class WallpaperQueueController(
    private val scope: CoroutineScope,
    private val context: Context
) {
    var loaded by mutableStateOf(false)
        private set
    var uris by mutableStateOf<Set<String>>(emptySet())
        private set
    var order by mutableStateOf<List<String>>(emptyList())
        private set

    private var saveJob: Job? = null

    /** Reads the persisted queue once; later calls are no-ops. */
    fun loadOnce() {
        if (loaded) return
        scope.launch {
            val state = withContext(Dispatchers.IO) { WallpaperQueueStore.load(context) }
            if (!loaded) {
                uris = state.uris
                order = state.order
                loaded = true
            }
        }
    }

    fun add(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val keys = items.map { it.uri.toString() }
        update(uris + keys, order + keys.filterNot { it in order })
    }

    fun remove(item: MediaItem) {
        update(uris - item.uri.toString(), order - item.uri.toString())
    }

    fun removeAll(keys: Set<String>) {
        update(uris - keys, order.filterNot { it in keys })
    }

    /** Replaces the queue wholesale (used by the restore path). */
    fun replace(newUris: Set<String>, newOrder: List<String>) {
        update(newUris, newOrder)
    }

    private fun update(newUris: Set<String>, newOrder: List<String>) {
        uris = newUris
        order = newOrder
        loaded = true
        persist(newUris, newOrder)
    }

    private fun persist(newUris: Set<String>, newOrder: List<String>) {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            WallpaperQueueStore.save(context, WallpaperQueueState(newUris, newOrder))
        }
    }
}
