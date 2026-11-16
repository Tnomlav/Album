package com.example.album.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File

/**
 * Frame grabber for videos the platform cannot decode.
 *
 * Some downloads are 10-bit H.264 (profile 110, "High 10"): the MediaStore
 * thumbnailer and MediaMetadataRetriever return nothing for them, so those
 * tiles had no preview at all. LibVLC ships its own decoders and its "scene"
 * video filter writes the first decoded frame to a PNG, which becomes a normal
 * thumbnail bitmap here.
 *
 * This only runs after the platform thumbnail failed. The result is cached by
 * the caller, so a file is decoded once.
 */
internal object VlcThumbnailExtractor {
    private const val TAG = "AlbumVlcThumb"
    private const val START_POSITION_SECONDS = 1.0
    private const val WAIT_TIMEOUT_MS = 8_000L
    private const val POLL_INTERVAL_MS = 150L

    @Volatile
    private var libVlc: LibVLC? = null

    private val failed = HashSet<String>()

    @Synchronized
    fun thumbnail(context: Context, uri: Uri, size: Int): Bitmap? {
        val key = uri.toString()
        if (key in failed) return null
        val directory = File(context.cacheDir, "vlc-thumbs").apply { mkdirs() }
        val bitmap = platformFrame(context, uri)?.let { frame ->
            scale(frame, size)
        } ?: runCatching { vlcFrame(context, uri, directory)?.let { scale(it, size) } }
            .onFailure { Log.w(TAG, "frame grab failed for $uri: ${it.message}") }
            .getOrNull()
        if (bitmap == null) failed += key
        return bitmap
    }

    private fun scale(decoded: Bitmap, size: Int): Bitmap {
        if (decoded.width < size && decoded.height < size) return decoded
        val ratio = size.toFloat() / maxOf(decoded.width, decoded.height)
        val scaled = decoded.scale(
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1)
        )
        if (scaled !== decoded) decoded.recycle()
        scaled.prepareToDraw()
        return scaled
    }

    /**
     * MediaMetadataRetriever sometimes succeeds where the provider's thumbnailer
     * gave up (its own timeout); it is much cheaper than starting LibVLC.
     */
    private fun platformFrame(context: Context, uri: Uri): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(
                (START_POSITION_SECONDS * 1_000_000).toLong(),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime
        } finally {
            runCatching { retriever.release() }
        }
    }.getOrNull()

    /**
     * VLC's scene filter writes decoded frames as images. The filter is set up
     * on the LibVLC instance (video filters are created with the output chain),
     * so the options live there rather than on each media.
     */
    private fun vlcFrame(context: Context, uri: Uri, directory: File): Bitmap? {
        directory.listFiles()?.forEach { it.delete() }
        val vlc = libVlc ?: LibVLC(
            context,
            arrayListOf(
                "--no-audio",
                "--no-spu",
                "--no-video-title-show",
                "--vout=dummy",
                "--video-filter=scene",
                "--scene-ratio=1",
                "--scene-format=png",
                "--scene-prefix=frame",
                "--scene-replace",
                "--scene-path=${directory.absolutePath}"
            )
        ).also { libVlc = it }

        // LibVLC cannot open content:// URIs itself; hand it the provider's file
        // descriptor and keep it open until the frame is written.
        val descriptor = if (uri.scheme?.lowercase() == "content") {
            runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        } else {
            null
        }
        try {
            val player = MediaPlayer(vlc)
            val media = descriptor?.fileDescriptor?.let { Media(vlc, it) } ?: Media(vlc, uri)
            media.addOption(":no-audio")
            media.addOption(":no-spu")
            media.addOption(":start-time=$START_POSITION_SECONDS")
            player.media = media
            media.release()
            player.play()

            val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
            var written: File? = null
            while (System.currentTimeMillis() < deadline) {
                written = directory.listFiles { file -> file.length() > 0 }?.maxByOrNull { it.lastModified() }
                if (written != null) break
                Thread.sleep(POLL_INTERVAL_MS)
            }
            runCatching { player.stop() }
            player.release()
            val png = written ?: return null
            val decoded = BitmapFactory.decodeFile(png.absolutePath)
            png.delete()
            return decoded
        } finally {
            runCatching { descriptor?.close() }
        }
    }
}
