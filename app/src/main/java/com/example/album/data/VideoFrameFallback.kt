package com.example.album.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.scale

/**
 * Second chance for a video thumbnail when the provider returned nothing.
 *
 * The provider's thumbnailer has its own timeout; MediaMetadataRetriever with a
 * frame timestamp sometimes succeeds where it gave up. Videos the platform
 * cannot decode at all (10-bit H.264 / High 10) still fall through to the
 * placeholder: LibVLC's scene filter is not available in the Android build, and
 * grabbing a frame would need an off-screen GL capture.
 */
internal object VideoFrameFallback {
    private const val FRAME_TIME_US = 1_500_000L

    /** URIs that already failed, so scrolling does not retry them every frame. */
    private val failed = HashSet<String>()

    @Synchronized
    fun thumbnail(context: Context, uri: Uri, size: Int): Bitmap? {
        val key = uri.toString()
        if (key in failed) return null
        val frame = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (frame == null) {
            failed += key
            return null
        }
        if (frame.width < size && frame.height < size) {
            frame.prepareToDraw()
            return frame
        }
        val ratio = size.toFloat() / maxOf(frame.width, frame.height)
        val scaled = frame.scale(
            (frame.width * ratio).toInt().coerceAtLeast(1),
            (frame.height * ratio).toInt().coerceAtLeast(1)
        )
        if (scaled !== frame) frame.recycle()
        scaled.prepareToDraw()
        return scaled
    }
}
