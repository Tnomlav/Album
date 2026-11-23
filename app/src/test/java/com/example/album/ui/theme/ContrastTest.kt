package com.example.album.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one thing a palette refactor must not regress: text contrast.
 *
 * These are the same pairs the UI actually uses — accent text on the page and
 * card surfaces, and the foreground of a filled accent button — for every
 * accent in both themes. A new colour that looks nice but cannot be read will
 * fail here instead of shipping.
 */
class ContrastTest {

    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertAtLeast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double
    ) {
        val actual = ratio(foreground, background)
        assertTrue(
            "$label: ${"%.2f".format(actual)}:1, expected >= ${"%.1f".format(minimum)}:1",
            actual >= minimum
        )
    }

    private val lightSurfaces = listOf(
        Color(0xFFFFFFFF), // surface / cards
        Color(0xFFF6F5F2)  // page background
    )

    private val darkSurfaces = listOf(
        Color(0xFF151817), // surface / cards
        Color(0xFF0C0E0D)  // page background
    )

    @Test
    fun accentTextIsReadableOnLightSurfaces() {
        for (accent in ThemeAccent.entries) {
            for (surface in lightSurfaces) {
                assertAtLeast(
                    accent.name + " text on light surface",
                    accent.palette.light,
                    surface,
                    4.5
                )
            }
        }
    }

    @Test
    fun accentTextIsReadableOnDarkSurfaces() {
        for (accent in ThemeAccent.entries) {
            for (surface in darkSurfaces) {
                assertAtLeast(
                    accent.name + " text on dark surface",
                    accent.palette.dark,
                    surface,
                    4.5
                )
            }
        }
    }

    @Test
    fun filledAccentButtonsHaveReadableForegrounds() {
        for (accent in ThemeAccent.entries) {
            assertAtLeast(
                "${accent.name} on light fill",
                accent.palette.onLight,
                accent.palette.light,
                4.5
            )
            assertAtLeast(
                "${accent.name} on dark fill",
                accent.palette.onDark,
                accent.palette.dark,
                4.5
            )
        }
    }

    @Test
    fun neutralTextIsReadableOnItsSurface() {
        assertAtLeast("light body", VaultInk, VaultBackground, 4.5)
        assertAtLeast("light muted", VaultMuted, VaultBackground, 4.5)
        assertAtLeast("light muted on surface", VaultMuted, VaultSurface, 4.5)
        assertAtLeast("dark body", Color(0xFFECEFEC), VaultDarkSurface, 4.5)
        assertAtLeast("dark muted", Color(0xFFA6AEA9), VaultDarkSurface, 4.5)
        assertAtLeast("dark muted on background", Color(0xFFA6AEA9), VaultDarkBackground, 4.5)
    }
}
