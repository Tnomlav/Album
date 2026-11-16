package com.example.album.ui.components

import com.example.album.ui.appText

/**
 * Touch regions for the player's double-tap seek and brightness/volume drag.
 *
 * A setting is a ratio such as "1:1:1" for the horizontal split, or the same
 * ratio prefixed with `v` ("v1:1:1") for a vertical split: landscape-style
 * left/right regions are the default, and the vertical variant puts rewind on
 * top and fast-forward at the bottom (brightness/volume on top/bottom for the
 * drag gesture).
 */
internal data class GestureSplit(val vertical: Boolean, val ratio: String)

internal fun parseGestureSplit(value: String): GestureSplit =
    if (value.startsWith("v")) GestureSplit(true, value.drop(1)) else GestureSplit(false, value)

/** Fraction used for zone lookup: the X axis for horizontal splits, Y for vertical. */
internal fun splitFraction(split: GestureSplit, x: Float, y: Float): Float =
    if (split.vertical) y else x

/** -1 rewind, 0 pause, 1 fast-forward. [x]/[y] are fractions of the gesture area. */
internal fun seekZone(x: Float, y: Float, setting: String): Int {
    val split = parseGestureSplit(setting)
    val fraction = splitFraction(split, x, y)
    return when (split.ratio) {
        "1:2:1" -> when {
            fraction < .25f -> -1
            fraction > .75f -> 1
            else -> 0
        }
        "1:0:1" -> if (fraction < .5f) -1 else 1
        else -> when {
            fraction < 1f / 3f -> -1
            fraction > 2f / 3f -> 1
            else -> 0
        }
    }
}

/**
 * 2 brightness, 3 volume, 0 neutral middle. The first region is brightness
 * (left for horizontal, top for vertical), the last is volume.
 */
internal fun brightnessVolumeZone(x: Float, y: Float, setting: String): Int {
    val split = parseGestureSplit(setting)
    val fraction = splitFraction(split, x, y)
    return when (split.ratio) {
        "1:1:1" -> when {
            fraction < 1f / 3f -> 2
            fraction > 2f / 3f -> 3
            else -> 0
        }
        "1:2:1" -> when {
            fraction < .25f -> 2
            fraction > .75f -> 3
            else -> 0
        }
        else -> if (fraction < .5f) 2 else 3
    }
}

/** "横向 1:1:1" / "竖向 1:0:1", for the settings list. */
internal fun gestureSplitLabel(value: String, english: Boolean): String {
    val split = parseGestureSplit(value)
    val axis = appText(if (split.vertical) "竖向" else "横向", english)
    return "$axis ${split.ratio}"
}
