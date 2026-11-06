package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSpeedOptionsTest {
    @Test
    fun `long press speed parses saved speed labels`() {
        assertEquals(2f, parseVideoSpeed("2x"), 0f)
        assertEquals(2.5f, parseVideoSpeed("2.5X"), 0f)
        assertEquals(1f, parseVideoSpeed("invalid"), 0f)
        assertEquals(4f, parseVideoSpeed("9x"), 0f)
    }

    @Test
    fun `long press never slows a faster normal playback speed`() {
        assertEquals(2f, longPressPlaybackSpeed(1f, 2f), 0f)
        assertEquals(4f, longPressPlaybackSpeed(4f, 2f), 0f)
        assertEquals(3f, longPressPlaybackSpeed(1.5f, 3f), 0f)
        assertEquals("2x", videoSpeedLabel(2f))
        assertEquals("2.5x", videoSpeedLabel(2.5f))
    }
}
