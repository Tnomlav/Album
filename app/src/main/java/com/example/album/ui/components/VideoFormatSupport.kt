package com.example.album.ui.components

import com.example.album.data.MediaItem

/**
 * Containers that neither Media3 nor the Android platform extractors can
 * demux. Those files are handed to the bundled LibVLC engine, which now drives
 * the same player screen as the platform player (see [VlcMedia3Player]).
 *
 * AVI (RIFF) and MPEG-PS (.mpg/.mpeg/.vob/...) used to be listed here, but
 * Media3's `AviExtractor` and `PsExtractor` read those containers, so they play
 * in the platform player and only fall back when the codec inside is
 * unsupported.
 */
internal fun requiresVlcPlayback(item: MediaItem): Boolean {
    val mime = item.mimeType.lowercase()
    val name = item.name.lowercase()
    if (mime.startsWith("audio/")) return false
    val extensions = listOf(
        ".wmv", ".asf", ".rmvb", ".rm", ".ogm"
    )
    return mime.contains("x-ms-wmv") ||
        mime.contains("x-ms-asf") ||
        mime.contains("vnd.rn-realmedia") ||
        extensions.any { name.endsWith(it) }
}

/**
 * Containers that Media3 demuxes with its own extractors (AVI, MPEG-PS)
 * instead of the platform ones. The platform player opens those files, but
 * when the codec inside is unsupported it can report no error at all and stall
 * on a black screen, so the player watches for a missing video track and
 * switches engines while keeping the same screen.
 */
internal fun usesMedia3LegacyContainer(item: MediaItem): Boolean {
    val mime = item.mimeType.lowercase()
    if (mime.startsWith("audio/")) return false
    val name = item.name.lowercase()
    return mime.contains("avi") ||
        mime.contains("x-msvideo") ||
        mime.contains("msvideo") ||
        mime.contains("divx") ||
        mime == "video/mp2p" ||
        name.endsWith(".avi") ||
        name.endsWith(".divx") ||
        name.endsWith(".xvid") ||
        name.endsWith(".mpg") ||
        name.endsWith(".mpeg") ||
        name.endsWith(".mpe") ||
        name.endsWith(".m1v") ||
        name.endsWith(".m2v") ||
        name.endsWith(".mpv") ||
        name.endsWith(".vob")
}
