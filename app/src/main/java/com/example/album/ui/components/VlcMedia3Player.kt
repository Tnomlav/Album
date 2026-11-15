package com.example.album.ui.components

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.graphics.SurfaceTexture
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import com.example.album.data.MediaItem as AlbumMediaItem
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * The bundled LibVLC engine exposed as a Media3 [Player].
 *
 * The viewer keeps one UI (the Media3 one) and one set of controls: it talks to
 * a `Player`, and this class makes LibVLC look like one. Files the platform
 * player cannot decode are routed here without changing the screen, the
 * gestures or the menus.
 *
 * Only the subset of the [Player] contract the viewer actually uses is
 * implemented; the rest falls back to [SimpleBasePlayer]'s defaults.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VlcMedia3Player(
    private val context: Context,
    private val items: List<AlbumMediaItem>,
    startIndex: Int,
    startPositionMs: Long
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val handler = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(
        context,
        arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--audio-time-stretch")
    )
    private val mediaPlayer = MediaPlayer(libVlc)

    private var currentIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    private var playbackState = Player.STATE_IDLE
    private var playWhenReady = true
    private var isLoading = false
    private var speed = 1f
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var videoWidth = 0
    private var videoHeight = 0
    private var audioTracks = 0
    private var durationMs = C.TIME_UNSET
    private var playerError: PlaybackException? = null
    private var firstFrameRendered = false
    private var released = false

    /** Position anchor: VLC only reports time in coarse steps. */
    private var anchorPositionMs = startPositionMs.coerceAtLeast(0L)
    private var anchorRealtimeMs = SystemClock.elapsedRealtime()
    private var pendingSeekMs = startPositionMs.coerceAtLeast(0L)

    private var media: Media? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var tickerRunning = false

    init {
        mediaPlayer.setEventListener { event -> onVlcEvent(event) }
        startTicker()
    }

    // ------------------------------------------------------------------ state

    private var playlistCache: List<MediaItemData> = emptyList()
    private var playlistCacheDurationMs = C.TIME_UNSET
    private var playlistCacheIndex = -1

    private fun playlist(): List<MediaItemData> {
        if (playlistCache.isNotEmpty() &&
            playlistCacheDurationMs == durationMs &&
            playlistCacheIndex == currentIndex
        ) {
            return playlistCache
        }
        playlistCache = items.mapIndexed { index, item ->
            MediaItemData.Builder(item.uri.toString())
                .setMediaItem(
                    androidx.media3.common.MediaItem.Builder()
                        .setUri(item.uri)
                        .setMediaId(item.uri.toString())
                        .setMimeType(item.mimeType.takeIf { it.startsWith("video/") && it != "video/*" })
                        .build()
                )
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.name)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .setDurationUs(
                    if (index == currentIndex && durationMs > 0) durationMs * 1000 else C.TIME_UNSET
                )
                .setIsSeekable(true)
                .setIsDynamic(false)
                .build()
        }
        playlistCacheDurationMs = durationMs
        playlistCacheIndex = currentIndex
        return playlistCache
    }

    override fun getState(): State {
        val playlist = playlist()
        return State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(currentIndex)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setPlaybackParameters(PlaybackParameters(speed))
            // SimpleBasePlayer rejects "loading" outside BUFFERING/READY, and a
            // Stopped event can arrive right after a new file starts opening.
            .setIsLoading(isLoading && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED)
            .setRepeatMode(repeatMode)
            .setShuffleModeEnabled(false)
            .setPlayerError(playerError)
            .setVideoSize(VideoSize(videoWidth, videoHeight))
            .setNewlyRenderedFirstFrame(firstFrameRendered && !firstFrameConsumed)
            .setContentPositionMs { positionMs() }
            .setContentBufferedPositionMs { positionMs() }
            .build()
            .also { firstFrameConsumed = firstFrameRendered }
    }

    private var firstFrameConsumed = false

    private fun positionMs(): Long {
        val raw = if (playWhenReady && playbackState == Player.STATE_READY) {
            anchorPositionMs +
                ((SystemClock.elapsedRealtime() - anchorRealtimeMs) * speed).toLong()
        } else {
            anchorPositionMs
        }
        val capped = if (durationMs > 0) raw.coerceIn(0L, durationMs) else raw.coerceAtLeast(0L)
        return capped
    }

    private fun reanchor(positionMs: Long) {
        anchorPositionMs = positionMs.coerceAtLeast(0L)
        anchorRealtimeMs = SystemClock.elapsedRealtime()
    }

    private fun currentTracks(): Tracks {
        val groups = ArrayList<Tracks.Group>(2)
        if (videoWidth > 0 && videoHeight > 0) {
            val format = Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_UNKNOWN)
                .setWidth(videoWidth)
                .setHeight(videoHeight)
                .build()
            groups += Tracks.Group(
                TrackGroup(format),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(true)
            )
        }
        if (audioTracks > 0) {
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_UNKNOWN).build()
            groups += Tracks.Group(
                TrackGroup(format),
                false,
                intArrayOf(C.FORMAT_HANDLED),
                booleanArrayOf(true)
            )
        }
        return if (groups.isEmpty()) Tracks.EMPTY else Tracks(groups)
    }

    // --------------------------------------------------------------- commands

    override fun handlePrepare(): ListenableFuture<*> {
        if (released) return Futures.immediateVoidFuture()
        loadCurrent(pendingSeekMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (released) return Futures.immediateVoidFuture()
        this.playWhenReady = playWhenReady
        runCatching {
            if (playWhenReady) {
                if (playbackState == Player.STATE_ENDED) {
                    mediaPlayer.time = 0L
                    reanchor(0L)
                }
                mediaPlayer.play()
            } else {
                mediaPlayer.pause()
                reanchor(positionMs())
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (released) return Futures.immediateVoidFuture()
        if (mediaItemIndex != currentIndex) {
            currentIndex = mediaItemIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
            loadCurrent(positionMs)
            return Futures.immediateVoidFuture()
        }
        reanchor(positionMs.coerceAtLeast(0L))
        pendingSeekMs = positionMs.coerceAtLeast(0L)
        runCatching { mediaPlayer.time = pendingSeekMs }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        val previousSpeed = speed
        speed = playbackParameters.speed.coerceIn(0.25f, 4f)
        if (speed != previousSpeed) {
            reanchor(positionMs())
            runCatching { mediaPlayer.rate = speed }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        runCatching { mediaPlayer.volume = (volume * 100).toInt() }
        return Futures.immediateVoidFuture()
    }

    private var viewsAttached = false
    private var videoView: android.view.View? = null
    private var layoutListener: android.view.View.OnLayoutChangeListener? = null
    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            attachCurrentView()
        }

        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            runCatching { mediaPlayer.updateVideoSurfaces() }
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            runCatching { mediaPlayer.detachViews() }
            viewsAttached = false
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        when (videoOutput) {
            is SurfaceView, is TextureView -> {
                val view = videoOutput as android.view.View
                bindVideoView(view)
                if (view is TextureView) view.surfaceTextureListener = textureListener
                attachCurrentView()
            }
            is SurfaceHolder -> {
                clearVideoViewListener()
                runCatching {
                    detachViewsIfNeeded()
                    mediaPlayer.vlcVout.setVideoSurface(videoOutput.surface, videoOutput)
                    mediaPlayer.vlcVout.attachViews()
                    viewsAttached = true
                }
            }
            is Surface -> {
                clearVideoViewListener()
                runCatching {
                    detachViewsIfNeeded()
                    mediaPlayer.vlcVout.setVideoSurface(videoOutput, null)
                    mediaPlayer.vlcVout.attachViews()
                    viewsAttached = true
                }
            }
        }
        return Futures.immediateVoidFuture()
    }

    private fun bindVideoView(view: android.view.View) {
        if (videoView === view) return
        clearVideoViewListener()
        videoView = view
        // The surface is normally attached before the first layout pass, when
        // its size is still 0; tell LibVLC about the real size once it is known,
        // otherwise the picture stays at its native size in a corner.
        val listener = android.view.View.OnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
            val width = r - l
            val height = b - t
            if (width > 0 && height > 0 && (width != or - ol || height != ob - ot)) {
                runCatching { mediaPlayer.updateVideoSurfaces() }
            }
        }
        layoutListener = listener
        view.addOnLayoutChangeListener(listener)
    }

    private fun clearVideoViewListener() {
        layoutListener?.let { listener -> videoView?.removeOnLayoutChangeListener(listener) }
        layoutListener = null
        (videoView as? TextureView)?.surfaceTextureListener = null
        videoView = null
    }

    private fun attachCurrentView() {
        val view = videoView ?: return
        runCatching {
            detachViewsIfNeeded()
            @Suppress("DEPRECATION")
            if (view is TextureView) mediaPlayer.vlcVout.setVideoView(view)
            else mediaPlayer.vlcVout.setVideoView(view as SurfaceView)
            mediaPlayer.vlcVout.attachViews()
            viewsAttached = true
            mediaPlayer.updateVideoSurfaces()
        }
    }

    /**
     * The VLC engine can own its own surface container instead of borrowing the
     * platform player's TextureView. [org.videolan.libvlc.util.VLCVideoLayout]
     * keeps the picture fitted to the view, which is what the previous
     * LibVLC-only screen used.
     */
    fun attachVideoLayout(layout: org.videolan.libvlc.util.VLCVideoLayout) {
        if (attachedLayout === layout && viewsAttached) return
        clearVideoViewListener()
        runCatching { mediaPlayer.detachViews() }
        viewsAttached = false
        attachedLayout = layout
        runCatching {
            mediaPlayer.attachViews(layout, null, false, false)
            viewsAttached = true
        }
    }

    private var attachedLayout: org.videolan.libvlc.util.VLCVideoLayout? = null

    private fun detachViewsIfNeeded() {
        if (!viewsAttached) return
        runCatching { mediaPlayer.detachViews() }
        viewsAttached = false
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        runCatching { mediaPlayer.detachViews() }
        viewsAttached = false
        clearVideoViewListener()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        handler.removeCallbacksAndMessages(null)
        runCatching { mediaPlayer.setEventListener(null) }
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        runCatching { mediaPlayer.release() }
        runCatching { media?.release() }
        media = null
        runCatching { descriptor?.close() }
        descriptor = null
        runCatching { libVlc.release() }
        return Futures.immediateVoidFuture()
    }

    // ---------------------------------------------------------------- loading

    private fun loadCurrent(positionMs: Long) {
        if (released) return
        playbackState = Player.STATE_BUFFERING
        isLoading = true
        playerError = null
        firstFrameRendered = false
        firstFrameConsumed = false
        durationMs = C.TIME_UNSET
        videoWidth = 0
        videoHeight = 0
        audioTracks = 0
        pendingSeekMs = positionMs.coerceAtLeast(0L)
        reanchor(pendingSeekMs)
        invalidateState()

        val item = items.getOrNull(currentIndex) ?: return
        runCatching { mediaPlayer.stop() }
        runCatching { media?.release() }
        media = null
        runCatching { descriptor?.close() }
        descriptor = null

        val created = runCatching { buildMedia(item.uri) }.getOrNull()
        if (created == null) {
            fail("Unable to open ${item.name}")
            return
        }
        media = created
        runCatching {
            mediaPlayer.media = created
            mediaPlayer.play()
            mediaPlayer.rate = speed
        }.onFailure { fail(it.message ?: "Unable to start LibVLC") }
        applyPendingSeek()
    }

    private fun buildMedia(uri: Uri): Media {
        val fromDescriptor = if (uri.scheme?.lowercase() == "content") {
            descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            descriptor?.fileDescriptor?.let { Media(libVlc, it) }
        } else {
            null
        }
        val media = fromDescriptor ?: Media(libVlc, uri)
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=300")
        return media
    }

    /**
     * LibVLC ignores a seek until the media is parsed, so the start position is
     * applied again shortly after opening and whenever the length shows up.
     */
    private fun applyPendingSeek() {
        if (pendingSeekMs <= 0L) return
        handler.postDelayed({
            if (released || pendingSeekMs <= 0L) return@postDelayed
            runCatching { mediaPlayer.time = pendingSeekMs }
            reanchor(pendingSeekMs)
            pendingSeekMs = 0L
            invalidateState()
        }, 400L)
    }

    private fun fail(message: String) {
        playbackState = Player.STATE_IDLE
        isLoading = false
        playerError = PlaybackException(
            message,
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
        invalidateState()
    }

    // ----------------------------------------------------------------- events

    private fun onVlcEvent(event: MediaPlayer.Event) {
        handler.post {
            if (released) return@post
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playbackState = Player.STATE_READY
                    isLoading = false
                    playWhenReady = true
                    reanchor(if (pendingSeekMs > 0L) pendingSeekMs else 0L)
                    readTrackInfo()
                    invalidateState()
                }
                MediaPlayer.Event.Paused -> {
                    if (playbackState != Player.STATE_ENDED) playbackState = Player.STATE_READY
                    reanchor(positionMs())
                    invalidateState()
                }
                MediaPlayer.Event.Stopped -> {
                    if (playbackState != Player.STATE_ENDED) playbackState = Player.STATE_IDLE
                    isLoading = false
                    invalidateState()
                }
                MediaPlayer.Event.EndReached -> {
                    playbackState = Player.STATE_ENDED
                    reanchor(durationMs.coerceAtLeast(0L))
                    invalidateState()
                }
                MediaPlayer.Event.EncounteredError -> {
                    fail("LibVLC could not decode this file")
                }
                MediaPlayer.Event.LengthChanged -> {
                    if (event.lengthChanged > 0) durationMs = event.lengthChanged
                    readTrackInfo()
                    invalidateState()
                }
                MediaPlayer.Event.TimeChanged -> {
                    if (pendingSeekMs <= 0L) reanchor(event.timeChanged.coerceAtLeast(0L))
                    if (!firstFrameRendered && videoWidth > 0) firstFrameRendered = true
                    invalidateState()
                }
                MediaPlayer.Event.Buffering -> {
                    isLoading = event.buffering < 100f
                    invalidateState()
                }
                MediaPlayer.Event.Vout -> {
                    readTrackInfo()
                    if (!firstFrameRendered && videoWidth > 0) firstFrameRendered = true
                    invalidateState()
                }
            }
        }
    }

    private fun readTrackInfo() {
        val track = runCatching { mediaPlayer.currentVideoTrack }.getOrNull()
        if (track != null) {
            if (track.width > 0) videoWidth = track.width
            if (track.height > 0) videoHeight = track.height
        }
        audioTracks = runCatching { mediaPlayer.audioTracksCount }.getOrDefault(0)
    }

    // ---------------------------------------------------------------- ticking

    /**
     * Keeps the seek bar moving between VLC's own time reports, which arrive
     * roughly four times per second.
     */
    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        val tick = object : Runnable {
            override fun run() {
                if (released) return
                if (playWhenReady && playbackState == Player.STATE_READY) invalidateState()
                handler.postDelayed(this, 250L)
            }
        }
        handler.postDelayed(tick, 250L)
    }
}
