package com.example.album.playback

import kotlin.math.roundToLong

private const val FINISHED_TOLERANCE_MS = 1_000L

internal val NORMAL_SKIP_OPTIONS = listOf("3秒", "5秒", "10秒", "15秒", "30秒", "5%")
internal val LONG_SKIP_OPTIONS = listOf("15秒", "30秒", "45秒", "60秒", "90秒", "120秒", "10%")

/**
 * Resolve a skip preference that may be either a fixed number of seconds
 * ("10秒") or a percentage of the current video ("5%"). Unknown values fall
 * back to the caller's default number of seconds.
 */
internal fun skipDurationMs(
    setting: String?,
    durationMs: Long,
    defaultSeconds: Long
): Long {
    val value = setting.orEmpty().trim()
    val safeDuration = durationMs.coerceAtLeast(0L)
    if (value.endsWith('%')) {
        val percent = value.dropLast(1).trim().toFloatOrNull() ?: return defaultSeconds * 1_000L
        return (safeDuration * percent / 100f).roundToLong().coerceAtLeast(0L)
    }
    val seconds = value.filter(Char::isDigit).toLongOrNull() ?: defaultSeconds
    return seconds.coerceAtLeast(0L) * 1_000L
}

internal fun resumePosition(savedPositionMs: Long, durationMs: Long): Long {
    if (savedPositionMs <= 0L) return 0L
    if (durationMs > 0L && savedPositionMs >= durationMs - FINISHED_TOLERANCE_MS) return 0L
    return if (durationMs > 0L) savedPositionMs.coerceAtMost(durationMs) else savedPositionMs
}

internal fun positionForPersistence(positionMs: Long, durationMs: Long, playbackEnded: Boolean): Long {
    if (playbackEnded) return 0L
    return resumePosition(positionMs, durationMs)
}
