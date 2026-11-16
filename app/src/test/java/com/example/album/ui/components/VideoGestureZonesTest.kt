package com.example.album.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureZonesTest {
    @Test
    fun `default brightness and volume split the screen in half`() {
        assertEquals(2, brightnessVolumeZone(.1f, .5f, "1:1"))
        assertEquals(2, brightnessVolumeZone(.49f, .5f, "1:1"))
        assertEquals(3, brightnessVolumeZone(.51f, .5f, "1:1"))
        assertEquals(3, brightnessVolumeZone(.9f, .5f, "1:1"))
    }

    @Test
    fun `equal three way split keeps a neutral middle`() {
        assertEquals(2, brightnessVolumeZone(.2f, .5f, "1:1:1"))
        assertEquals(0, brightnessVolumeZone(.5f, .5f, "1:1:1"))
        assertEquals(3, brightnessVolumeZone(.8f, .5f, "1:1:1"))
    }

    @Test
    fun `one two one split widens the neutral middle`() {
        assertEquals(2, brightnessVolumeZone(.2f, .5f, "1:2:1"))
        assertEquals(0, brightnessVolumeZone(.5f, .5f, "1:2:1"))
        assertEquals(3, brightnessVolumeZone(.8f, .5f, "1:2:1"))
    }

    @Test
    fun `default double tap uses thirds`() {
        assertEquals(-1, seekZone(.1f, .5f, "1:1:1"))
        assertEquals(0, seekZone(.5f, .5f, "1:1:1"))
        assertEquals(1, seekZone(.9f, .5f, "1:1:1"))
    }

    @Test
    fun `one two one double tap keeps the pause zone wider`() {
        assertEquals(-1, seekZone(.2f, .5f, "1:2:1"))
        assertEquals(0, seekZone(.5f, .5f, "1:2:1"))
        assertEquals(1, seekZone(.8f, .5f, "1:2:1"))
    }

    @Test
    fun `one zero one double tap has no pause zone`() {
        assertEquals(-1, seekZone(.1f, .5f, "1:0:1"))
        assertEquals(-1, seekZone(.49f, .5f, "1:0:1"))
        assertEquals(1, seekZone(.51f, .5f, "1:0:1"))
        assertEquals(1, seekZone(.9f, .5f, "1:0:1"))
    }

    @Test
    fun `vertical seek puts rewind on top and forward at the bottom`() {
        assertEquals(-1, seekZone(.5f, .1f, "v1:1:1"))
        assertEquals(0, seekZone(.5f, .5f, "v1:1:1"))
        assertEquals(1, seekZone(.5f, .9f, "v1:1:1"))
    }

    @Test
    fun `vertical one zero one seek has no pause row`() {
        assertEquals(-1, seekZone(.5f, .1f, "v1:0:1"))
        assertEquals(-1, seekZone(.5f, .49f, "v1:0:1"))
        assertEquals(1, seekZone(.5f, .51f, "v1:0:1"))
        assertEquals(1, seekZone(.5f, .9f, "v1:0:1"))
        // Horizontal position must not matter for a vertical split.
        assertEquals(-1, seekZone(.95f, .2f, "v1:0:1"))
        assertEquals(1, seekZone(.05f, .8f, "v1:0:1"))
    }

    @Test
    fun `vertical brightness and volume split top and bottom`() {
        assertEquals(2, brightnessVolumeZone(.5f, .1f, "v1:1:1"))
        assertEquals(0, brightnessVolumeZone(.5f, .5f, "v1:1:1"))
        assertEquals(3, brightnessVolumeZone(.5f, .9f, "v1:1:1"))
        assertEquals(2, brightnessVolumeZone(.5f, .2f, "v1:0:1"))
        assertEquals(3, brightnessVolumeZone(.5f, .8f, "v1:0:1"))
    }

    @Test
    fun `label names the axis and the ratio`() {
        assertEquals("横向 1:1:1", gestureSplitLabel("1:1:1", english = false))
        assertEquals("竖向 1:0:1", gestureSplitLabel("v1:0:1", english = false))
        assertEquals("Vertical 1:0:1", gestureSplitLabel("v1:0:1", english = true))
    }
}
