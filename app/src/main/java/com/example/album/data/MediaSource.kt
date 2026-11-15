package com.example.album.data

/**
 * One place the library can be read from.
 *
 * The refresh pipeline used to call the MediaStore and the SAF folder walker
 * directly. Splitting them behind this interface gives every source the same
 * three questions — what is there now, what changed since the last scan, and
 * which keys are still alive — which is what makes an incremental refresh
 * possible without a second scan implementation per source.
 *
 * Sources that cannot answer a delta (SAF has no reliable modified-since query)
 * return null from the delta methods; callers fall back to [queryAll].
 */
interface MediaSource {
    /** Human-readable name, used in logs and error messages. */
    val name: String

    /** Everything this source currently holds. */
    suspend fun queryAll(): List<MediaItem>

    /**
     * Items whose content changed since [sinceMillis] (epoch milliseconds), or
     * null when this source cannot answer that question cheaply.
     */
    suspend fun queryChangedSince(sinceMillis: Long): List<MediaItem>?

    /**
     * Keys (URIs) currently present, or null when listing them would cost as
     * much as a full scan.
     */
    suspend fun aliveKeys(): Set<String>?
}

/** MediaStore images, optionally narrowed to rows changed since a timestamp. */
class MediaStoreImageSource(private val repository: MediaRepository) : MediaSource {
    override val name = "images"

    override suspend fun queryAll(): List<MediaItem> = repository.loadImages()

    override suspend fun queryChangedSince(sinceMillis: Long): List<MediaItem> =
        repository.loadImagesSince(sinceMillis / 1000L)

    override suspend fun aliveKeys(): Set<String> = repository.aliveUris(isVideo = false)
}

/** MediaStore videos, mirroring [MediaStoreImageSource]. */
class MediaStoreVideoSource(private val repository: MediaRepository) : MediaSource {
    override val name = "videos"

    override suspend fun queryAll(): List<MediaItem> = repository.loadVideos()

    override suspend fun queryChangedSince(sinceMillis: Long): List<MediaItem> =
        repository.loadVideosSince(sinceMillis / 1000L)

    override suspend fun aliveKeys(): Set<String> = repository.aliveUris(isVideo = true)
}
