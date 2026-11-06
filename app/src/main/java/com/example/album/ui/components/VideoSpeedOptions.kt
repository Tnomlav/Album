package com.example.album.ui.components

internal val PLAYBACK_SPEED_OPTIONS =
    listOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "4x")

internal val LONG_PRESS_SPEED_OPTIONS =
    listOf("1.5x", "2x", "2.5x", "3x", "4x")

internal fun parseVideoSpeed(value: String?, default: Float = 1f): Float {
    val parsed = value
        ?.trim()
        ?.removeSuffix("x")
        ?.removeSuffix("X")
        ?.toFloatOrNull()
    return (parsed ?: default).coerceIn(.25f, 4f)
}

/**
 * Long press always accelerates relative to the current playback speed, even
 * when the user chose a faster normal speed than the saved long-press rate.
 */
internal fun longPressPlaybackSpeed(normalSpeed: Float, selectedLongPressSpeed: Float): Float =
    maxOf(normalSpeed, selectedLongPressSpeed).coerceIn(.25f, 4f)

internal fun videoSpeedLabel(speed: Float): String =
    "${speed.toString().removeSuffix(".0")}x"
