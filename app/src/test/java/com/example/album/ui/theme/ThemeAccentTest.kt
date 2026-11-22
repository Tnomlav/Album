package com.example.album.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeAccentTest {
    @Test
    fun storedThemeColorsRoundTrip() {
        ThemeAccent.entries.forEach { accent ->
            assertEquals(accent, ThemeAccent.fromStored(accent.storedValue.lowercase()))
        }
    }

    @Test
    fun unknownThemeColorFallsBackToPixivBlue() {
        assertEquals(ThemeAccent.Blue, ThemeAccent.fromStored("#123456"))
        assertEquals(ThemeAccent.Blue, ThemeAccent.fromStored(null))
    }
}
