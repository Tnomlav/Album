package com.example.album.ui

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.album.data.MediaItem
import com.example.album.data.folderKey
import com.example.album.data.isSystemTrashedFile
import com.example.album.data.MediaRepository
import com.example.album.data.LocalFolderRepository
import com.example.album.data.CleanupRepository
import com.example.album.data.DuplicateGroup
import com.example.album.data.DuplicateScanSnapshot
import com.example.album.data.DuplicateUiSnapshot
import com.example.album.data.RecycleEntry
import com.example.album.data.ConflictPolicy
import com.example.album.data.TransferResult
import com.example.album.data.ThumbnailRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.ContextCompat
import androidx.core.content.edit

private data class RefreshResult(
    val images: List<MediaItem>,
    val videos: List<MediaItem>,
    val local: List<MediaItem>,
    val recycle: List<RecycleEntry>,
    val errors: List<String>
)

private data class PreparedMedia(
    val images: List<MediaItem>,
    val videos: List<MediaItem>,
    val localImages: List<MediaItem>,
    val localVideos: List<MediaItem>,
    val visibleImages: List<MediaItem>,
    val visibleVideos: List<MediaItem>,
    val excluded: List<MediaItem>
)

private data class IndexedFolderSnapshot(
    val names: Set<String>,
    val children: Map<String, Set<String>>,
    val files: Map<String, Set<String>>,
    val updatedAt: Long
)

class MediaLibraryState(context: Context) {
    private val appContext = context.applicationContext
    private val repository = MediaRepository(appContext)
    private val imageSource: com.example.album.data.MediaSource =
        com.example.album.data.MediaStoreImageSource(repository)
    private val videoSource: com.example.album.data.MediaSource =
        com.example.album.data.MediaStoreVideoSource(repository)

    /**
     * When the last successful scan finished (epoch millis). Seeded from the
     * snapshot's savedAt, so a restart can still do an incremental refresh.
     */
    private var lastScanAtMillis: Long = 0L

    /**
     * The unfiltered MediaStore rows of the last scan.
     *
     * The delta has to be merged into these, not into [images] / [allImages]:
     * those have the hidden and R-18 filters applied, so a merge would come out
     * short and always trigger the full-scan fallback.
     */
    private var rawImages: List<MediaItem> = emptyList()
    private var rawVideos: List<MediaItem> = emptyList()

    /** Observer refreshes may read only the changed rows when this is on. */
    private val incrementalRefreshEnabled: Boolean
        get() = settingsPreferences.getBoolean("incremental_refresh", true)
    private val localFolders = LocalFolderRepository(appContext)
    private val cleanup = CleanupRepository(appContext)
    private val cleanupPreferences = context.getSharedPreferences("cleanup_preferences", Context.MODE_PRIVATE)
    private val settingsPreferences = context.getSharedPreferences("album_settings", Context.MODE_PRIVATE)
    private val folderIndexFile = File(appContext.filesDir, "searchable_folder_index.json")
    private var allImages: List<MediaItem> = emptyList()
    private var allVideos: List<MediaItem> = emptyList()
    private val refreshMutex = Mutex()
    private val folderNamesMutex = Mutex()
    private val folderIndexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var folderIndexJob: Job? = null
    private var folderIndexJobAccessKey: String? = null
    private var folderNamesAccessKey: String? = null
    private var folderIndexUpdatedAt = 0L
    private companion object {
        // A cached directory index is cheap to reuse. Refresh it at most once
        // per two minutes unless a caller explicitly invalidates it.
        const val FOLDER_INDEX_REFRESH_INTERVAL_MS = 2 * 60 * 1000L
    }
    private val retentionDays = settingsPreferences.getString("retention", "60")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    private var maintenanceReady = false

    fun close() {
        folderIndexJob?.cancel()
        folderIndexScope.cancel()
    }

    var images by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var videos by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var localImages by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var localVideos by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    /**
     * Everything the UI needs to know about loading: whether the first scan
     * finished, which scan is running, and which one is waiting behind it.
     *
     * This used to be three independent flags (`loading`, `refreshSource`,
     * `initialLoadComplete`); a refresh that was queued behind another scan
     * could not be expressed, so pull-to-refresh needed extra UI-side flags.
     */
    var loadState by mutableStateOf(LoadState())
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set
    var scanning by mutableStateOf(false)
        private set
    var permissionGranted by mutableStateOf(false)
        private set
    var imagePermissionGranted by mutableStateOf(false)
        private set
    var videoPermissionGranted by mutableStateOf(false)
        private set
    var hasLocalFolders by mutableStateOf(false)
        private set
    var localFolderCount by mutableIntStateOf(0)
        private set
    var searchableFolderNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var searchableFoldersLoading by mutableStateOf(false)
        private set
    var searchableFoldersReady by mutableStateOf(false)
        private set
    var searchableFolderChildren by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var searchableFolderFiles by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var excludedFolders by mutableStateOf(cleanupPreferences.getStringSet("excluded_folders", emptySet()).orEmpty().toSet())
        private set
    var excludedMedia by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    // Load recycle metadata during refresh instead of blocking construction.
    var recycleEntries by mutableStateOf<List<RecycleEntry>>(emptyList())
        private set

    /** Signature of the snapshot last written to disk; unchanged scans skip the write. */
    private var lastSavedSnapshotKey: String? = null

    init {
        // Publish the snapshot this process has already parsed. Reading and
        // parsing the file here used to stall the first frame: on a large
        // library the JSON is several megabytes. A cold start now loads it from
        // disk in refresh(), which already runs on Dispatchers.IO.
        com.example.album.data.MediaSnapshotStore.cached()?.let { cached ->
            allImages = cached.images
            allVideos = cached.videos
            localImages = cached.localImages
            localVideos = cached.localVideos
            images = cached.images
            videos = cached.videos
            lastScanAtMillis = cached.savedAt
            loadState = loadState.copy(initialScanComplete = true)
        }
    }

    /* init {
        val days = settingsPreferences.getString("retention", "60天")?.filter(Char::isDigit)?.toIntOrNull() ?: 60
    } */

    /**
     * Marks a request as waiting before its coroutine reaches the mutex, so the
     * UI can show "the pull is being served" in the same frame as the gesture
     * instead of after the queued scan actually starts.
     */
    fun markQueued(source: RefreshSource) {
        loadState = loadState.copy(queued = source)
    }

    /** Clears [source] from the queue when its request never reached the mutex (cancelled). */
    fun clearQueued(source: RefreshSource) {
        if (loadState.queued == source) loadState = loadState.copy(queued = null)
    }

    suspend fun refresh(
        granted: Boolean,
        scheduleThumbnailOptimization: Boolean = true,
        source: RefreshSource = RefreshSource.Operation
    ) = refreshMutex.withLock {
        permissionGranted = granted
        loadState = loadState.copy(active = source, queued = null)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        refreshError = null
        try {
            // Show the previous snapshot immediately; MediaStore queries on a
            // large library can take seconds.
            if (images.isEmpty() && videos.isEmpty() && localImages.isEmpty() && localVideos.isEmpty()) {
                withContext(Dispatchers.IO) { com.example.album.data.MediaSnapshotStore.load(appContext) }?.let { cached ->
                    allImages = cached.images
                    allVideos = cached.videos
                    localImages = cached.localImages
                    localVideos = cached.localVideos
                    images = cached.images
                    videos = cached.videos
                    rawImages = cached.images
                    rawVideos = cached.videos
                    if (lastScanAtMillis == 0L) lastScanAtMillis = cached.savedAt
                }
            }
            val canReadImages = granted && hasImageReadAccess(appContext)
            val canReadVideos = granted && hasVideoReadAccess(appContext)
            // Only the background observer refresh is worth doing incrementally:
            // a pull or an operation expects the current truth, and both are
            // rare enough that a full scan is acceptable there.
            val incremental = source == RefreshSource.Observer &&
                incrementalRefreshEnabled &&
                lastScanAtMillis > 0L &&
                System.currentTimeMillis() - lastScanAtMillis < INCREMENTAL_MAX_AGE_MS &&
                (rawImages.isNotEmpty() || rawVideos.isNotEmpty())
            imagePermissionGranted = canReadImages
            videoPermissionGranted = canReadVideos
            val refreshResult = coroutineScope {
                val errors = ConcurrentLinkedQueue<String>()
                val imagesTask = async {
                    if (!canReadImages) emptyList()
                    else try {
                        readSource(imageSource, rawImages, incremental)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        errors.add("图片读取失败")
                        emptyList()
                    }
                }
                val videosTask = async {
                    if (!canReadVideos) emptyList()
                    else try {
                        readSource(videoSource, rawVideos, incremental)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        errors.add("视频读取失败")
                        emptyList()
                    }
                }
                val localTask = async {
                    try {
                        localFolders.loadMedia()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        errors.add("本地文件夹读取失败")
                        emptyList()
                    }
                }
                val maintenanceTask = async(Dispatchers.IO) {
                    if (!maintenanceReady) {
                        try {
                            cleanup.purgeExpired(retentionDays)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Maintenance failure must not prevent the library from loading.
                        }
                        maintenanceReady = true
                    }
                    try {
                        cleanup.loadRecycleEntries()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                RefreshResult(imagesTask.await(), videosTask.await(), localTask.await(), maintenanceTask.await(), errors.toList())
            }
            refreshError = refreshResult.errors.takeIf { it.isNotEmpty() }?.joinToString("、")
            rawImages = refreshResult.images
            rawVideos = refreshResult.videos
            lastScanAtMillis = System.currentTimeMillis()
            val prepared = withContext(Dispatchers.Default) {
                val showHiddenMedia = settingsPreferences.getBoolean("show_hidden_media", false)
                // The R-18 filter only needs the identities the archiver
                // recorded while writing tags: no file has to be read here.
                val adultFilter = if (com.example.album.data.AdultTagStore.enabled(appContext)) {
                    com.example.album.data.AdultTagStore.load(appContext)
                } else {
                    com.example.album.data.AdultTagFilter()
                }
                fun visibleName(item: MediaItem): Boolean =
                    !item.isSystemTrashedFile() &&
                        (showHiddenMedia || !item.name.trimStart().startsWith('.'))
                // The R-18 filter is about pictures; videos keep their own
                // handling (they rarely carry the archive's tag set).
                fun visibleImage(item: MediaItem): Boolean = visibleName(item) && !adultFilter.contains(item)
                val normalizedImages = refreshResult.images.filter(::visibleImage).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val normalizedVideos = refreshResult.videos.filter(::visibleName).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val normalizedLocalImages = refreshResult.local.filterNot { it.isVideo }.filter(::visibleImage).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val normalizedLocalVideos = refreshResult.local.filter { it.isVideo }.filter(::visibleName).distinctBy { it.uri }.sortedByDescending { it.dateTaken }
                val excluded = excludedFolders
                PreparedMedia(
                    images = normalizedImages,
                    videos = normalizedVideos,
                    localImages = normalizedLocalImages,
                    localVideos = normalizedLocalVideos,
                    visibleImages = normalizedImages.filterNot { it.folder in excluded },
                    visibleVideos = normalizedVideos.filterNot { it.folder in excluded },
                    excluded = (normalizedImages + normalizedVideos).filter { it.folder in excluded }
                )
            }
            withContext(Dispatchers.IO) { cleanup.reconcileRecycleBin() }
            recycleEntries = cleanup.loadRecycleEntries()
            val localFolderCountSnapshot = withContext(Dispatchers.IO) { localFolders.treeUris().size }
            hasLocalFolders = localFolderCountSnapshot > 0
            localFolderCount = localFolderCountSnapshot
            // Reusing the previous list instance when the content is equal
            // skips the whole-library recomposition that a background refresh
            // used to trigger even when nothing changed.
            allImages = prepared.images.reusedIfSame(allImages)
            allVideos = prepared.videos.reusedIfSame(allVideos)
            localImages = prepared.localImages.reusedIfSame(localImages)
            localVideos = prepared.localVideos.reusedIfSame(localVideos)
            images = prepared.visibleImages.reusedIfSame(images)
            videos = prepared.visibleVideos.reusedIfSame(videos)
            excludedMedia = prepared.excluded.reusedIfSame(excludedMedia)
            // Writing the whole library JSON on every ContentObserver refresh made
            // repeated scans noticeably longer. Only persist when the visible
            // content actually changed.
            val snapshotKey = listOf(
                prepared.images.size,
                prepared.videos.size,
                prepared.localImages.size,
                prepared.localVideos.size,
                prepared.images.firstOrNull()?.uri?.toString(),
                prepared.images.lastOrNull()?.uri?.toString(),
                prepared.videos.firstOrNull()?.uri?.toString()
            ).joinToString("|")
            if (snapshotKey != lastSavedSnapshotKey) {
                withContext(Dispatchers.IO) {
                    com.example.album.data.MediaSnapshotStore.save(
                        appContext,
                        com.example.album.data.MediaSnapshotStore.Snapshot(
                            images = prepared.images,
                            videos = prepared.videos,
                            localImages = prepared.localImages,
                            localVideos = prepared.localVideos,
                            savedAt = System.currentTimeMillis()
                        )
                    )
                }
                lastSavedSnapshotKey = snapshotKey
            }
            // Hashing the library to decide on background thumbnails is not
            // free; keep it off the main thread so the refresh spinner keeps
            // its frames.
            withContext(Dispatchers.Default) {
                if (scheduleThumbnailOptimization) {
                    ThumbnailRepository.scheduleBackgroundOptimization(
                        appContext,
                        (prepared.images + prepared.videos).distinctBy { it.uri },
                        settingsPreferences
                    )
                } else {
                    ThumbnailRepository.cancelBackgroundOptimization()
                }
            }
        } finally {
            // A pull that finishes in a few milliseconds would otherwise only
            // flash the indicator; keep it up long enough to read.
            if (source == RefreshSource.UserPull) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                if (elapsed < MIN_PULL_INDICATOR_MS) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        kotlinx.coroutines.delay(MIN_PULL_INDICATOR_MS - elapsed)
                    }
                }
            }
            loadState = loadState.copy(active = null, initialScanComplete = true)
            // One cache-audit line per library scan, next to the per-200
            // progress lines: pulling to refresh on a big library is enough to
            // read the thumbnail memory/disk hit rate off logcat.
            withContext(Dispatchers.Default) { ThumbnailRepository.logStats("after scan") }
        }
    }

    /**
     * Reads one MediaStore source, using the delta when it is trustworthy and
     * falling back to a full scan whenever it is not.
     */
    private suspend fun readSource(
        source: com.example.album.data.MediaSource,
        current: List<MediaItem>,
        incremental: Boolean
    ): List<MediaItem> {
        if (!incremental) return source.queryAll()
        val changed = source.queryChangedSince(lastScanAtMillis) ?: return source.queryAll()
        val alive = source.aliveKeys() ?: return source.queryAll()
        val merged = com.example.album.data.mergeChangedItems(current, changed, alive) {
            it.uri.toString()
        }
        // A row the store has but the delta missed would silently disappear;
        // rescan instead.
        return if (merged.complete) merged.items else source.queryAll()
    }

    private suspend fun refreshSearchableFolderIndex(accessKey: String) {
        val names = linkedSetOf<String>()
        val children = linkedMapOf<String, MutableSet<String>>()
        val files = linkedMapOf<String, MutableSet<String>>()
        // Empty and non-media folders are not in MediaStore, so they can only
        // come from the storage walk. Publish the growing index while the walk
        // is still running instead of waiting for the very end, otherwise
        // searching for a folder without media appears much slower than one
        // that is already known from the media scan.
        var lastPublishAt = 0L
        fun publishProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPublishAt < 250L) return
            lastPublishAt = now
            searchableFolderNames = names.toSet()
            searchableFolderChildren = children.mapValues { (_, childNames) -> childNames.toSet() }
            searchableFolderFiles = files.mapValues { (_, fileNames) -> fileNames.toSet() }
            searchableFoldersReady = true
        }
        try {
            localFolders.streamFolderNames(
                onBatch = { batch ->
                    names += batch
                    publishProgress()
                },
                onChildBatch = { childBatch ->
                    childBatch.forEach { (parent, childNames) ->
                        children.getOrPut(parent) { linkedSetOf() } += childNames
                    }
                    publishProgress()
                },
                onFileBatch = { fileBatch ->
                    fileBatch.forEach { (folder, fileNames) ->
                        files.getOrPut(folder) { linkedSetOf() } += fileNames
                    }
                    publishProgress()
                }
            )
            writeFolderIndex(accessKey, names, children, files)
            withContext(Dispatchers.Main.immediate) {
                folderNamesAccessKey = accessKey
                folderIndexUpdatedAt = System.currentTimeMillis()
                searchableFolderNames = names.toSet()
                searchableFolderChildren = children.mapValues { (_, childNames) -> childNames.toSet() }
                searchableFolderFiles = files.mapValues { (_, fileNames) -> fileNames.toSet() }
                searchableFoldersLoading = false
                searchableFoldersReady = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                searchableFoldersLoading = false
                searchableFoldersReady = true
            }
        }
    }

    suspend fun loadSearchableFolderNames() = folderNamesMutex.withLock {
        val accessKey = buildString {
            append(localFolders.treeUris().map(Uri::toString).sorted().joinToString("|"))
            append(";allFiles=")
            append(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager())
        }
        if (folderNamesAccessKey == accessKey &&
            folderIndexUpdatedAt > 0L &&
            System.currentTimeMillis() - folderIndexUpdatedAt < FOLDER_INDEX_REFRESH_INTERVAL_MS
        ) return@withLock
        if (folderIndexJob?.isActive == true && folderIndexJobAccessKey != accessKey) {
            folderIndexJob?.cancel()
            folderIndexJob = null
            folderIndexJobAccessKey = null
        }
        searchableFolderNames = emptySet()
        searchableFolderChildren = emptyMap()
        searchableFolderFiles = emptyMap()
        searchableFoldersLoading = true
        searchableFoldersReady = false
        // Reuse the last complete directory index immediately. Empty and
        // non-media folders are not represented by MediaStore, so this cache
        // keeps them searchable without making every app start scan storage.
        val loadedCache = readFolderIndex(accessKey)
        if (loadedCache) {
            folderNamesAccessKey = accessKey
            searchableFoldersLoading = false
            searchableFoldersReady = true
            val cacheIsFresh =
                folderIndexUpdatedAt > 0L &&
                System.currentTimeMillis() - folderIndexUpdatedAt < FOLDER_INDEX_REFRESH_INTERVAL_MS
            if (!cacheIsFresh && folderIndexJob?.isActive != true) {
                folderIndexJobAccessKey = accessKey
                folderIndexJob = folderIndexScope.launch {
                    refreshSearchableFolderIndex(accessKey)
                }
            }
            return@withLock
        }
        if (folderIndexJob?.isActive != true) {
            folderIndexJobAccessKey = accessKey
            folderIndexJob = folderIndexScope.launch {
                refreshSearchableFolderIndex(accessKey)
            }
        }
    }

    private suspend fun readFolderIndex(accessKey: String): Boolean {
        val snapshot = withContext(Dispatchers.IO) {
            runCatching {
                val root = JSONObject(folderIndexFile.readText())
                if (root.optString("accessKey") != accessKey) return@runCatching null
                val names = root.optJSONArray("names").toStringSet()
                val children = root.optJSONObject("children").toSetMap()
                val files = root.optJSONObject("files").toSetMap()
                val updatedAt = root.optLong("updatedAt", 0L)
                IndexedFolderSnapshot(names, children, files, updatedAt)
            }.getOrNull()
        } ?: return false
        withContext(Dispatchers.Main.immediate) {
            searchableFolderNames = snapshot.names
            searchableFolderChildren = snapshot.children
            searchableFolderFiles = snapshot.files
        }
        folderIndexUpdatedAt = snapshot.updatedAt
        return true
    }

    private suspend fun writeFolderIndex(
        accessKey: String,
        names: Set<String>,
        children: Map<String, Set<String>>,
        files: Map<String, Set<String>>
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().put("accessKey", accessKey)
                .put("updatedAt", System.currentTimeMillis())
                .put("names", JSONArray(names.toList()))
                .put("children", children.toJsonObject())
                .put("files", files.toJsonObject())
            val temporary = File(folderIndexFile.parentFile, "${folderIndexFile.name}.tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(folderIndexFile)) {
                temporary.delete()
            }
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        if (this@toStringSet == null) return@buildSet
        repeat(this@toStringSet!!.length()) { index ->
            this@toStringSet!!.optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    private fun JSONObject?.toSetMap(): Map<String, Set<String>> = buildMap {
        val source = this@toSetMap ?: return@buildMap
        source.keys().forEach { key ->
            put(key, source.optJSONArray(key).toStringSet())
        }
    }

    private fun Map<String, Set<String>>.toJsonObject(): JSONObject = JSONObject().also { result ->
        forEach { (key, values) -> result.put(key, JSONArray(values.toList())) }
    }

    suspend fun scanAndRefresh(granted: Boolean, userInitiated: Boolean = false): MediaScanResult {
        if (!userInitiated) return MediaScanResult.NotRequested
        if (!granted) return MediaScanResult.PermissionRequired
        scanning = true
        return try {
            val scanned = try {
                repository.scanPublicMedia()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return MediaScanResult.Failed(error.message)
            }
            refresh(granted)
            MediaScanResult.Completed(scanned)
        } finally {
            scanning = false
        }
    }

    fun setBackgroundOptimization(enabled: Boolean) {
        if (enabled) {
            ThumbnailRepository.scheduleBackgroundOptimization(
                appContext,
                (allImages + allVideos).distinctBy { it.uri },
                settingsPreferences
            )
        } else {
            ThumbnailRepository.cancelBackgroundOptimization()
        }
    }

    suspend fun setShowHiddenMedia(enabled: Boolean) {
        settingsPreferences.edit().putBoolean("show_hidden_media", enabled).apply()
        refresh(permissionGranted)
    }

    /**
     * Hides (or shows again) the pictures the archiver tagged as adult content.
     * A refresh re-applies the filter, exactly like the hidden-file toggle.
     */
    suspend fun setHideAdultTagged(enabled: Boolean) {
        settingsPreferences.edit().putBoolean("hide_adult_tagged", enabled).apply()
        refresh(permissionGranted)
    }

    fun remove(item: MediaItem) {
        if (item.isDocument) {
            if (item.isVideo) localVideos = localVideos.filterNot { it.uri == item.uri }
            else localImages = localImages.filterNot { it.uri == item.uri }
        } else if (item.isVideo) allVideos = allVideos.filterNot { it.uri == item.uri }
        else allImages = allImages.filterNot { it.uri == item.uri }
        applyExclusions()
    }

    fun remove(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.mapTo(hashSetOf()) { it.uri }
        allImages = allImages.filterNot { it.uri in uris }
        allVideos = allVideos.filterNot { it.uri in uris }
        localImages = localImages.filterNot { it.uri in uris }
        localVideos = localVideos.filterNot { it.uri in uris }
        applyExclusions()
    }

    private var pendingRemovalUris by mutableStateOf<Set<Uri>>(emptySet())

    /** Removes items from every visible list while their file operation runs. */
    fun removeOptimistically(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pendingRemovalUris = pendingRemovalUris + items.map { it.uri }
        remove(items)
    }

    /**
     * Puts items back after an optimistic file operation failed or required
     * user consent. This only restores the in-memory view; the repository and
     * MediaStore are still authoritative for the next refresh.
     */
    fun restore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pendingRemovalUris = pendingRemovalUris - items.mapTo(hashSetOf()) { it.uri }
        val restored = items.distinctBy { it.uri }
        val existingUris = (allImages + allVideos + localImages + localVideos)
            .mapTo(hashSetOf()) { it.uri }
        val missing = restored.filterNot { it.uri in existingUris }
        allImages = (allImages + missing.filter { !it.isDocument && !it.isVideo }).distinctBy { it.uri }
        allVideos = (allVideos + missing.filter { !it.isDocument && it.isVideo }).distinctBy { it.uri }
        localImages = (localImages + missing.filter { it.isDocument && !it.isVideo }).distinctBy { it.uri }
        localVideos = (localVideos + missing.filter { it.isDocument && it.isVideo }).distinctBy { it.uri }
        applyExclusions()
    }

    fun finishRemoval(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pendingRemovalUris = pendingRemovalUris - items.mapTo(hashSetOf()) { it.uri }
        applyExclusions()
    }

    private fun replaceItem(existing: MediaItem, replacement: MediaItem) {
        allImages = allImages.map { if (it.uri == existing.uri) replacement else it }
        allVideos = allVideos.map { if (it.uri == existing.uri) replacement else it }
        localImages = localImages.map { if (it.uri == existing.uri) replacement else it }
        localVideos = localVideos.map { if (it.uri == existing.uri) replacement else it }
        applyExclusions()
    }

    suspend fun deleteLegacy(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching { repository.delete(item) > 0 }.getOrDefault(false)
    }

    /**
     * Deletes one item and says *why* a failure happened. On Android 10 a delete
     * of media owned by another app throws [RecoverableSecurityException] instead
     * of failing outright: the caller has to show the system's per-file consent
     * dialog and retry, and that needs the IntentSender handed back here.
     */
    suspend fun deleteMedia(item: MediaItem): DeleteOutcome = withContext(Dispatchers.IO) {
        try {
            if (repository.delete(item) > 0) DeleteOutcome.Deleted else DeleteOutcome.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            consentSenderOrNull(error)?.let(DeleteOutcome::NeedsConsent) ?: DeleteOutcome.Failed
        }
    }

    suspend fun rename(item: MediaItem, newName: String): MediaItem? {
        val optimistic = item.copy(name = newName)
        replaceItem(item, optimistic)
        return withContext(Dispatchers.IO) {
            val renamed = try {
                repository.rename(item, newName)
            } catch (cancelled: CancellationException) {
                replaceItem(optimistic, item)
                throw cancelled
            } catch (_: Throwable) {
                replaceItem(optimistic, item)
                return@withContext null
            } ?: run {
                replaceItem(optimistic, item)
                return@withContext null
            }
            replaceItem(optimistic, renamed)
            renamed
        }
    }

    fun renameFolder(folder: String, newName: String): Int {
        val matching = (allImages + allVideos).filter { it.folderKey() == folder }
        val renamed = repository.renameFolder(matching, newName)
        if (renamed > 0) applyExclusions()
        return renamed
    }

    suspend fun createFolder(parentItems: List<MediaItem>, name: String): Boolean {
        val parent = parentItems.firstOrNull() ?: return false
        val created = if (parent.isDocument) {
            localFolders.createFolderForFile(parent.uri, name)
        } else {
            repository.createFolder(parent, name)
        }
        if (created) refresh(permissionGranted)
        return created
    }

    suspend fun createTransferFolder(path: String, items: List<MediaItem>, name: String): Boolean {
        val localCreated = localFolders.createFolderAtPath(path, name)
        if (localCreated) return true

        val mediaKinds = items.map { it.isVideo }.distinct()
        return mediaKinds.any { isVideo -> repository.createFolderAtPath(path, name, isVideo) }
    }

    /** Checks the exact relative path before showing it as a recent target. */
    suspend fun isTransferFolderAvailable(path: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = path.trim('/').replace('\\', '/')
        if (normalized.isBlank() || normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            return@withContext false
        }
        localFolders.findAuthorizedDirectory(normalized)?.let { directory ->
            if (directory.isDirectory && directory.exists()) return@withContext true
        }
        File(Environment.getExternalStorageDirectory(), normalized).isDirectory
    }

    suspend fun transfer(
        items: List<MediaItem>,
        destinationFolder: String,
        conflictPolicy: ConflictPolicy,
        preserveModifiedDate: Boolean,
        mode: com.example.album.data.TransferMode = com.example.album.data.TransferMode.Copy,
        onProgress: (Float) -> Unit = {}
    ): List<TransferResult> = coroutineScope {
        // SAF and MediaStore both benefit from a small amount of parallelism,
        // while unbounded jobs make real devices slower due to I/O contention.
        val gate = Semaphore(3)
        // Bytes are summed per item so three parallel copies still produce one
        // monotonic overall percentage.
        val totalBytes = items.sumOf { it.size.coerceAtLeast(1L) }.coerceAtLeast(1L)
        val doneBytes = java.util.concurrent.atomic.AtomicLong(0L)
        items.map { item ->
            async {
                gate.withPermit {
                    var reported = 0L
                    repository.transfer(item, destinationFolder, conflictPolicy, preserveModifiedDate, mode) { copied ->
                        val delta = (copied - reported).coerceAtLeast(0L)
                        reported = copied
                        val done = doneBytes.addAndGet(delta.coerceAtMost(item.size.coerceAtLeast(1L)))
                        onProgress((done.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                }
            }
        }.awaitAll()
    }

    suspend fun addLocalFolder(uri: android.net.Uri) {
        val folderCount = withContext(Dispatchers.IO) {
            localFolders.addTree(uri)
            localFolders.treeUris().size
        }
        hasLocalFolders = true
        localFolderCount = folderCount
        refresh(permissionGranted)
    }

    fun excludeFolder(folder: String) {
        excludedFolders = excludedFolders + folder
        cleanupPreferences.edit().putStringSet("excluded_folders", excludedFolders).apply()
        applyExclusions()
    }

    fun restoreExcludedFolder(folder: String) {
        excludedFolders = excludedFolders - folder
        cleanupPreferences.edit { putStringSet("excluded_folders", excludedFolders) }
        applyExclusions()
    }

    suspend fun findDuplicates(
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> = cleanup.findDuplicateGroups(
        (allImages + localImages).distinctBy { it.uri.toString() },
        onProgress
    )

    suspend fun loadDuplicateScanSnapshot(): DuplicateScanSnapshot =
        cleanup.loadDuplicateScanSnapshot(
            (allImages + localImages).distinctBy { it.uri.toString() }
        )

    fun loadDuplicateUiSnapshot(): DuplicateUiSnapshot = cleanup.loadDuplicateUiSnapshot()

    fun saveDuplicateUiSnapshot(snapshot: DuplicateUiSnapshot) =
        cleanup.saveDuplicateUiSnapshot(snapshot)

    suspend fun stageForRecycle(items: List<MediaItem>): List<RecycleEntry> {
        val staged = cleanup.stageForRecycle(items)
        recycleEntries = cleanup.loadRecycleEntries()
        return staged
    }

    suspend fun stageForSystemRecycle(items: List<MediaItem>): List<RecycleEntry> {
        val staged = cleanup.stageForSystemRecycle(items)
        recycleEntries = cleanup.loadRecycleEntries()
        return staged
    }

    fun discardRecycle(ids: Set<String>) {
        cleanup.discardRecycleEntries(ids)
        recycleEntries = cleanup.loadRecycleEntries()
    }

    suspend fun restoreRecycle(entry: RecycleEntry): Boolean {
        val restored = cleanup.restore(entry)
        recycleEntries = cleanup.loadRecycleEntries()
        if (restored) refresh(permissionGranted, scheduleThumbnailOptimization = false)
        return restored
    }

    suspend fun restoreRecycle(entries: List<RecycleEntry>): List<RecycleEntry> {
        val restored = cleanup.restore(entries)
        recycleEntries = cleanup.loadRecycleEntries()
        if (restored.isNotEmpty()) refresh(permissionGranted, scheduleThumbnailOptimization = false)
        return restored
    }

    fun permanentlyDeleteRecycle(entry: RecycleEntry) {
        cleanup.removeRecycleEntry(entry)
        recycleEntries = cleanup.loadRecycleEntries()
    }

    suspend fun permanentlyDeleteRecycle(entries: List<RecycleEntry>) {
        withContext(Dispatchers.IO) { cleanup.removeRecycleEntries(entries) }
        recycleEntries = cleanup.loadRecycleEntries()
    }

    fun removeRecycleRecords(entries: List<RecycleEntry>) {
        cleanup.discardRecycleEntries(entries.mapTo(mutableSetOf()) { it.id })
        recycleEntries = cleanup.loadRecycleEntries()
    }

    fun purgeExpiredRecycle(retentionDays: Int) {
        cleanup.purgeExpired(retentionDays)
        recycleEntries = cleanup.loadRecycleEntries()
    }

    val indexedMediaCount: Int
        get() = allImages.size + allVideos.size + localImages.size + localVideos.size

    private fun applyExclusions() {
        localImages = localImages.filterNot { it.uri in pendingRemovalUris }
        localVideos = localVideos.filterNot { it.uri in pendingRemovalUris }
        images = allImages.filter { it.folder !in excludedFolders && it.uri !in pendingRemovalUris }
        videos = allVideos.filter { it.folder !in excludedFolders && it.uri !in pendingRemovalUris }
        excludedMedia = (allImages + allVideos).filter {
            it.folder in excludedFolders && it.uri !in pendingRemovalUris
        }
    }
}

sealed interface MediaScanResult {
    data class Completed(val scannedFiles: Int) : MediaScanResult
    data class Failed(val reason: String?) : MediaScanResult
    data object PermissionRequired : MediaScanResult
    data object NotRequested : MediaScanResult
}

/** Keeps the previous instance when two lists hold the same values. */
private fun <T> List<T>.reusedIfSame(previous: List<T>): List<T> =
    if (this == previous) previous else this

/** Outcome of one delete attempt; see [MediaLibraryState.deleteMedia]. */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** The system wants the user to approve this single file before it goes. */
    data class NeedsConsent(val sender: IntentSender) : DeleteOutcome

    data object Failed : DeleteOutcome
}

/**
 * The IntentSender of the consent dialog for a delete the system refused. Only
 * Android 10 (API 29) reports it this way: newer versions either delete directly
 * or are routed through MediaStore.createDeleteRequest.
 */
internal fun consentSenderOrNull(error: Throwable): IntentSender? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException) {
        runCatching { error.userAction.actionIntent.intentSender }.getOrNull()
    } else {
        null
    }

private fun hasImageReadAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

private fun hasVideoReadAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/** Minimum time the pull indicator stays visible once a finger pull started it. */
private const val MIN_PULL_INDICATOR_MS = 450L

/**
 * How stale a scan may be before the observer refresh stops trusting deltas.
 * Older timestamps make a full scan cheaper than reconciling provider quirks.
 */
private const val INCREMENTAL_MAX_AGE_MS = 6L * 60L * 60L * 1000L
