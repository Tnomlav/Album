package com.example.album.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolboxOrderTest {
    @Test
    fun upgradeAddsSearchModulesWithoutLosingCustomOrder() {
        val migrated = normalizeToolboxOrder(
            listOf("slideshow", "archive", "wallpaper", "cleanup", "pixiv")
        )

        assertEquals(
            listOf("cleanup", "pixiv", "slideshow", "wallpaper", "saucenao", "ascii2d"),
            migrated
        )
        assertFalse(migrated.contains("archive"))
    }

    @Test
    fun emptyAndDuplicateOrdersNormalizeToEveryToolOnce() {
        val normalized = normalizeToolboxOrder(
            listOf("ascii2d", "wallpaper", "ascii2d", "unknown")
        )

        assertEquals(
            listOf("cleanup", "pixiv", "wallpaper", "slideshow", "saucenao", "ascii2d"),
            normalized
        )
        assertEquals(normalized.size, normalized.distinct().size)
    }
}
