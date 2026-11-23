package com.example.album.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared design tokens.
 *
 * These are the values the UI already used most often. Call sites that matched
 * a token exactly were migrated mechanically, so this layer changes no pixels;
 * the remaining one-off values (7/15/18dp, 220/240ms, ...) are intentionally
 * left alone until a visual design pass decides how to snap them.
 */
object VaultShape {
    val Small = RoundedCornerShape(4.dp)
    val Medium = RoundedCornerShape(8.dp)
    val Large = RoundedCornerShape(12.dp)
    val XLarge = RoundedCornerShape(16.dp)
    val Full = CircleShape
}

/** Animation durations in milliseconds; pass to `tween()`. */
object VaultMotion {
    const val Fast = 120
    const val Standard = 200
    const val Slow = 300
}

/**
 * 4dp spacing scale. New layouts should use these instead of raw dp values;
 * existing call sites are migrated by the same "exact match only" rule as
 * [VaultShape].
 */
object VaultSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
}
