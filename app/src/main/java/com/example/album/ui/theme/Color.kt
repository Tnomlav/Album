package com.example.album.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val VaultGreen = Color(0xFFFA007D)
val VaultBlue = Color(0xFF0096FA)
val VaultTeal = Color(0xFF4B00FA)
val VaultOrange = Color(0xFFFA7D00)
val VaultCoral = Color(0xFFFAD000)
val VaultViolet = Color(0xFF9200FA)
val VaultForest = Color(0xFF1E8E3E)
val VaultBackground = Color(0xFFF7F7F7)
val VaultSurface = Color(0xFFFFFFFF)
val VaultInk = Color(0xFF202624)
val VaultMuted = Color(0xFF6B736F)
val VaultSecondarySurface = Color(0xFFFCFCFC)
val VaultDarkBackground = Color(0xFF121715)
val VaultDarkSurface = Color(0xFF1B211E)

/**
 * Neutral chrome for full-screen surfaces (viewer, editor).
 *
 * In the light theme the chrome is white. In the dark theme white would glare,
 * so regions are separated by depth instead: canvas is pure black, the bars one
 * step up, and controls/panels one more. Image overlays (crop handles, swatch
 * checks) deliberately keep their own white/black contrast and do not use this
 * palette.
 */
object VaultChrome {
    val LightCanvas = Color(0xFFFFFFFF)
    val LightBar = Color(0xFFFFFFFF)
    val LightPanel = Color(0xFFFFFFFF)
    val LightRaised = Color(0xFFF4F4F2)
    val LightContent = Color(0xFF232725)
    val LightMuted = Color(0xFF777B79)
    val LightLine = Color(0xFFD8DAD7)

    val DarkCanvas = Color(0xFF000000)
    val DarkBackdrop = Color(0xFF0B0D0C)
    val DarkBar = Color(0xFF0E100F)
    val DarkPanel = Color(0xFF161A18)
    val DarkRaised = Color(0xFF202522)
    val DarkContent = Color(0xFFEDEFED)
    val DarkMuted = Color(0xFF9AA39E)
    val DarkLine = Color(0xFF2C332F)
}

/**
 * Darkens a theme accent until white text on it reaches WCAG AA (4.5:1).
 *
 * The light theme uses these variants for primary text and filled buttons;
 * the dark theme keeps the original vivid colours because they sit on a dark
 * surface. The calculation keeps the hue by scaling RGB together.
 */
internal fun lightAccentForText(color: Color): Color {
    var scale = 1f
    var result = color
    while (result.luminance() > 0.18f && scale > 0.2f) {
        scale -= 0.04f
        result = Color(
            red = color.red * scale,
            green = color.green * scale,
            blue = color.blue * scale,
            alpha = color.alpha
        )
    }
    return result
}

enum class ThemeAccent(val label: String, val storedValue: String, val color: Color) {
    Blue("Pixiv 蓝", "#0096FA", VaultBlue),
    Green("玫红", "#FA007D", VaultGreen),
    Teal("靛蓝", "#4B00FA", VaultTeal),
    Orange("活力橙", "#FA7D00", VaultOrange),
    Coral("金色", "#FAD000", VaultCoral),
    Violet("紫罗兰", "#9200FA", VaultViolet),
    Forest("森林绿", "#1E8E3E", VaultForest);

    companion object {
        /** Display order follows the rainbow: magenta, orange, gold, green, blue, indigo, violet. */
        val rainbow = listOf(Green, Orange, Coral, Forest, Blue, Teal, Violet)

        fun fromStored(value: String?): ThemeAccent = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        } ?: Blue
    }
}
