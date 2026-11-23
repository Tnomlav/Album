package com.example.album.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent colours as a small palette instead of a single value.
 *
 * [hue] is the colour people pick in Settings. [light] and [dark] are the
 * variants safe to use as text/icon colour on the app's light and dark
 * surfaces (WCAG AA, 4.5:1); [onLight]/[onDark] are the matching foregrounds
 * for a filled button. Keeping the three roles separate is what stops the
 * gold theme from turning a 12sp label into a 1.49:1 smudge.
 */
data class AccentPalette(
    val hue: Color,
    val light: Color,
    val dark: Color,
    val onLight: Color,
    val onDark: Color
)

val VaultBackground = Color(0xFFF6F5F2)
val VaultSurface = Color(0xFFFFFFFF)
val VaultInk = Color(0xFF171918)
val VaultMuted = Color(0xFF5C6360)
val VaultSecondarySurface = Color(0xFFF0EFEA)
val VaultDarkBackground = Color(0xFF0C0E0D)
val VaultDarkSurface = Color(0xFF151817)
val VaultLine = Color(0xFFE2E0DA)
val VaultDarkLine = Color(0xFF2A302C)
val VaultDarkRaised = Color(0xFF1C201E)

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
    val LightMuted = Color(0xFF6B706D)
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

enum class ThemeAccent(
    val label: String,
    val storedValue: String,
    val palette: AccentPalette
) {
    Blue(
        "蓝", "#0096FA",
        AccentPalette(Color(0xFF0096FA), Color(0xFF0068B0), Color(0xFF0096FA), Color.White, Color(0xFF101512))
    ),
    Green(
        "玫红", "#FA007D",
        AccentPalette(Color(0xFFFA007D), Color(0xFFC20063), Color(0xFFFA007D), Color.White, Color(0xFF101512))
    ),
    Teal(
        "靛蓝", "#4B00FA",
        AccentPalette(Color(0xFF4B00FA), Color(0xFF4B00FA), Color(0xFF9A66FF), Color.White, Color(0xFF101512))
    ),
    Orange(
        "橙", "#FA7D00",
        AccentPalette(Color(0xFFFA7D00), Color(0xFFA34A00), Color(0xFFFA7D00), Color.White, Color(0xFF101512))
    ),
    Coral(
        "金", "#FAD000",
        AccentPalette(Color(0xFFFAD000), Color(0xFF7A5B00), Color(0xFFFAD000), Color.White, Color(0xFF101512))
    ),
    Violet(
        "紫", "#9200FA",
        AccentPalette(Color(0xFF9200FA), Color(0xFF9200FA), Color(0xFFB45CFF), Color.White, Color(0xFF101512))
    ),
    Forest(
        "绿", "#1E8E3E",
        AccentPalette(Color(0xFF1E8E3E), Color(0xFF16702F), Color(0xFF5FC77D), Color.White, Color(0xFF101512))
    );

    /** The vivid colour, for swatches and other non-text decoration. */
    val color: Color get() = palette.hue

    companion object {
        /** Display order follows the rainbow: magenta, orange, gold, green, blue, indigo, violet. */
        val rainbow = listOf(Green, Orange, Coral, Forest, Blue, Teal, Violet)

        fun fromStored(value: String?): ThemeAccent = entries.firstOrNull {
            it.storedValue.equals(value, ignoreCase = true)
        } ?: Blue
    }
}
