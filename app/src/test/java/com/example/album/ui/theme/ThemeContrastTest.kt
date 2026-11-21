package com.example.album.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the light-theme tokens against the contrast regressions in Chapter 0.5. */
class ThemeContrastTest {
    private fun contrast(foreground: Color, background: Color): Double {
        val first = foreground.luminance().toDouble()
        val second = background.luminance().toDouble()
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    @Test
    fun lightAccentTextMeetsAaOnWhite() {
        ThemeAccent.entries.forEach { accent ->
            val text = lightAccentForText(accent.color)
            val ratio = contrast(text, Color.White)
            assertTrue(
                "${accent.label} text contrast on white is %.2f:1".format(ratio),
                ratio >= 4.5
            )
        }
    }

    @Test
    fun whiteTextOnLightAccentMeetsAa() {
        ThemeAccent.entries.forEach { accent ->
            val ratio = contrast(Color.White, lightAccentForText(accent.color))
            assertTrue(
                "${accent.label} white-on-accent contrast is %.2f:1".format(ratio),
                ratio >= 4.5
            )
        }
    }

    @Test
    fun mutedTextMeetsAaOnWhite() {
        val ratio = contrast(VaultMuted, Color.White)
        assertTrue("muted text contrast is %.2f:1".format(ratio), ratio >= 4.5)
    }
}
