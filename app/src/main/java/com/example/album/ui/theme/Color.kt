package com.example.album.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val VaultGreen = Color(0xFF00FA68)
val VaultBlue = Color(0xFF0096FA)
val VaultTeal = Color(0xFF00FAE5)
val VaultOrange = Color(0xFFFA7D00)
val VaultCoral = Color(0xFFFA0000)
val VaultViolet = Color(0xFF9200FA)
val VaultBackground = Color(0xFFFFFFFF)
val VaultSurface = Color(0xFFFFFFFF)
val VaultInk = Color(0xFF202624)
val VaultMuted = Color(0xFF6B736F)
val VaultDarkBackground = Color(0xFF121715)
val VaultDarkSurface = Color(0xFF1B211E)

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
    Green("荧光绿", "#00FA68", VaultGreen),
    Teal("青绿色", "#00FAE5", VaultTeal),
    Orange("活力橙", "#FA7D00", VaultOrange),
    Coral("珊瑚红", "#FA0000", VaultCoral),
    Violet("紫罗兰", "#9200FA", VaultViolet);

    companion object {
        fun fromStored(value: String?): ThemeAccent = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        } ?: Blue
    }
}
