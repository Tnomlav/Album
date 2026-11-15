package com.example.album.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.album.data.MediaItem
import com.example.album.data.PixivArchiveRepository
import com.example.album.data.PixivLibrarySnapshot
import com.example.album.data.PixivWalkStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loading and refresh state of the Pixiv page, lifted out of `AlbumApp`.
 *
 * The page owns a cached snapshot, an optional SAF walk and the pull-to-refresh
 * gesture's rules (only a finger pull shows the indicator, only a finger pull
 * forces a fresh walk, partial snapshots only feed the first paint). Keeping
 * that in one class makes the rules testable and keeps `AlbumApp` from holding
 * another seven states.
 */
class PixivPageController(
    private val scope: CoroutineScope,
    private val repository: PixivArchiveRepository
) {
    /** The library the page renders; null before anything loaded. */
    var images by mutableStateOf<List<MediaItem>?>(null)
        private set
    var folderNames by mutableStateOf(setOf("Pixiv"))
        private set
    var sourceFolderName by mutableStateOf("Pixiv")
        private set
    var walkStats by mutableStateOf<PixivWalkStats?>(null)
        private set
    var tagsByUri by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set

    /**
     * Bumped only when a full walk finishes. Partial snapshots update [images]
     * but must not restart the expensive tag index build.
     */
    var version by mutableIntStateOf(0)
        private set

    /** True only while a finger pull is being served. */
    var refreshing by mutableStateOf(false)
        private set

    private var generation = 0
    private var reloadJob: Job? = null

    fun replaceTags(tags: Map<String, List<String>>) {
        tagsByUri = tags
    }

    fun addTags(entries: Map<String, List<String>>) {
        if (entries.isEmpty()) return
        tagsByUri = tagsByUri + entries
    }

    /** Parses the on-disk snapshot while the app starts, off the main thread. */
    fun loadCachedInBackground() {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { repository.loadCachedLibrary() } }
        }
    }

    /**
     * @param visible whether the Pixiv page is the one on screen; a request made
     * from another page must not walk (and must not leave the indicator behind).
     * @param defaultItems the local Pixiv folder, used when no archive is set up.
     * @param showIndicator true only for the finger pull.
     */
    fun request(
        visible: () -> Boolean,
        defaultItems: List<MediaItem>,
        forceWalk: Boolean = true,
        showIndicator: Boolean = false
    ) {
        reloadJob?.cancel()
        // Light the indicator up now: waiting for the 350ms debounce below left
        // the gesture's spinner sitting still until the walk got going.
        if (showIndicator) refreshing = true
        reloadJob = scope.launch {
            // Triggers often arrive together (a library refresh plus an explicit
            // request); the short wait collapses them into one SAF walk instead
            // of cancelling and restarting a walk that just started.
            if (forceWalk) delay(350L)
            reload(visible, defaultItems, forceWalk, showIndicator)
        }
    }

    private suspend fun reload(
        visible: () -> Boolean,
        defaultItems: List<MediaItem>,
        forceWalk: Boolean,
        showIndicator: Boolean
    ) {
        if (!visible()) {
            // The request was made while another page is showing: it never
            // walks, so it must not leave the indicator it lit up running.
            refreshing = false
            return
        }
        val currentGeneration = ++generation
        var walkStartedAt = 0L
        try {
            // Show the previous snapshot immediately (SAF tree walks are slow)
            // and replace it once the fresh walk finishes.
            if (images == null) {
                withContext(Dispatchers.IO) { repository.loadCachedLibrary() }?.let { cached ->
                    if (currentGeneration != generation) return
                    images = cached.items
                    folderNames = cached.folderNames
                    sourceFolderName = cached.sourceFolderName
                    walkStats = cached.walkStats
                    version++
                }
            }
            if (!forceWalk && images != null) {
                // Nothing to do: the cached snapshot (which survives app
                // restarts) is what the page shows.
                return
            }
            walkStartedAt = System.nanoTime()
            // A full SAF walk can take several seconds. Partial snapshots are
            // streamed through a conflated channel so folders appear as soon as
            // they are read instead of all at the end.
            val progress = Channel<PixivLibrarySnapshot>(Channel.CONFLATED)
            val publishPartials = images == null
            val progressJob = scope.launch {
                for (partial in progress) {
                    if (currentGeneration != generation) break
                    // While a refresh runs, keep the complete list on screen
                    // instead of replacing it with the half-walked one.
                    if (!publishPartials) continue
                    images = partial.items
                    folderNames = partial.folderNames
                    sourceFolderName = partial.sourceFolderName
                    walkStats = partial.walkStats
                }
            }
            val snapshot = try {
                repository.loadLibrary(
                    defaultItems,
                    onProgress = { partial -> progress.trySend(partial) },
                    // A finger pull is the user asking for the current tree, not
                    // for a replay of the last walk.
                    forceFresh = showIndicator
                )
            } finally {
                progress.close()
                progressJob.cancel()
            }
            if (currentGeneration != generation) return
            images = snapshot.items
            tagsByUri = snapshot.tagsByUri
            folderNames = snapshot.folderNames
            sourceFolderName = snapshot.sourceFolderName
            walkStats = snapshot.walkStats
            version++
        } finally {
            // The latest request always clears the flag, even when it returned
            // early from the cached snapshot: an older cancelled walk used to
            // leave the pull-to-refresh spinner running for good.
            if (showIndicator && walkStartedAt > 0L) {
                val elapsedMs = (System.nanoTime() - walkStartedAt) / 1_000_000L
                if (elapsedMs < MIN_PIXIV_REFRESH_INDICATOR_MS) {
                    delay(MIN_PIXIV_REFRESH_INDICATOR_MS - elapsedMs)
                }
            }
            if (currentGeneration == generation) refreshing = false
        }
    }
}

/** Minimum time the Pixiv pull indicator stays visible once a finger pull started it. */
private const val MIN_PIXIV_REFRESH_INDICATOR_MS = 700L
