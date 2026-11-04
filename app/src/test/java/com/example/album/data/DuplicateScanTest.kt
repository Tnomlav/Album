package com.example.album.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins both the byte-identical path and the strict visual fallback. */
class DuplicateScanTest {

    private fun stream(text: String) = ByteArrayInputStream(text.toByteArray())

    @Test
    fun identicalFilesMatch() {
        assertTrue(sameContent(stream("hello world"), stream("hello world")))
    }

    @Test
    fun emptyFilesMatch() {
        assertTrue(sameContent(stream(""), stream("")))
    }

    @Test
    fun aDifferenceInTheMiddleDoesNotMatch() {
        val left = "a".repeat(200_000) + "X" + "b".repeat(200_000)
        val right = "a".repeat(200_000) + "Y" + "b".repeat(200_000)
        assertFalse(sameContent(stream(left), stream(right)))
    }

    @Test
    fun differentLengthsDoNotMatch() {
        assertFalse(sameContent(stream("same prefix, different tail"), stream("same prefix, different")))
        assertFalse(sameContent(stream("short"), stream("")))
    }

    @Test
    fun onlyFilesWithTheSameSizeAndShapeBecomeCandidates() {
        val sizes = listOf(10L, 20L, 10L, 30L, 20L, 0L)
        val widths = listOf(100, 100, 100, 100, 100, 100)
        val heights = listOf(200, 200, 200, 200, 200, 200)
        // 10 appears twice (0, 2) and 20 twice (1, 4); the zero size is skipped.
        assertEquals(listOf(0, 2, 1, 4), duplicateCandidates(sizes, widths, heights))
        assertTrue(duplicateCandidates(listOf(1L, 2L, 3L), widths, heights).isEmpty())
        // Same size, different shape: cannot be byte-identical.
        assertTrue(
            duplicateCandidates(
                sizes = listOf(10L, 10L),
                widths = listOf(100, 120),
                heights = listOf(200, 200)
            ).isEmpty()
        )
        // Unknown shape falls back to the size-only group rather than being skipped.
        assertEquals(
            listOf(0, 1),
            duplicateCandidates(
                sizes = listOf(10L, 10L),
                widths = listOf(0, 0),
                heights = listOf(0, 0)
            )
        )
    }

    @Test
    fun theCheckNeverLooksAtNamesOrPictures() {
        // The same file name or the same artwork id must not matter: only the
        // bytes do, so the candidate step works for any picture on the device.
        assertEquals(
            listOf(0, 1),
            duplicateCandidates(
                sizes = listOf(64L, 64L),
                widths = listOf(1200, 1200),
                heights = listOf(1600, 1600)
            )
        )
    }

    private fun fingerprint(
        perceptualHash: Long = 0b1010101010101010L,
        differenceHash: Long = 0b0101010101010101L,
        luminance: IntArray = IntArray(16 * 16) { if (it % 2 == 0) 30 else 110 },
        red: Float = 100f,
        green: Float = 100f,
        blue: Float = 100f,
        aspect: Float = 1.5f
    ) = ImageFingerprint(
        perceptualHash = perceptualHash,
        differenceHash = differenceHash,
        luminance16 = luminance,
        red = red,
        green = green,
        blue = blue,
        aspect = aspect
    )

    @Test
    fun aReencodedPictureStillMatches() {
        val base = fingerprint()
        val reencoded = base.copy(
            perceptualHash = base.perceptualHash xor 0b111L,
            differenceHash = base.differenceHash xor 0b111L,
            luminance16 = base.luminance16.copyOf().also { grid ->
                grid.indices.forEach { grid[it] += 2 }
            },
            red = 102f,
            green = 98f,
            blue = 101f
        )
        assertTrue(looksLikeSamePicture(base, reencoded))
    }

    @Test
    fun differentPicturesAndEditsDoNotMatch() {
        val base = fingerprint()
        assertFalse(looksLikeSamePicture(base, base.copy(perceptualHash = base.perceptualHash xor 0b11111L)))
        assertFalse(looksLikeSamePicture(base, base.copy(differenceHash = base.differenceHash xor 0b111111L)))
        assertFalse(looksLikeSamePicture(base, base.copy(red = 200f, green = 60f, blue = 40f)))
        assertFalse(looksLikeSamePicture(base, base.copy(aspect = 1.1f)))
        assertFalse(
            looksLikeSamePicture(
                base,
                base.copy(luminance16 = IntArray(16 * 16) { if (it % 2 == 0) 110 else 30 })
            )
        )
    }

    @Test
    fun flatAndDegenerateHashesAreNeverOfferedForVisualDeletion() {
        val flat = fingerprint(
            perceptualHash = 0L,
            differenceHash = -1L,
            luminance = IntArray(16 * 16) { 80 }
        )
        assertFalse(looksLikeSamePicture(flat, flat.copy(perceptualHash = 1L, differenceHash = 0L)))
        assertTrue(strictPerceptualGroups(listOf(flat, flat.copy(perceptualHash = 1L))).isEmpty())
    }

    @Test
    fun groupingDoesNotChainWhereOnlyAdjacentPicturesMatch() {
        val baseLuma = IntArray(16 * 16) { if (it % 2 == 0) 20 else 120 }
        val first = fingerprint(perceptualHash = 1L, differenceHash = 3L, luminance = baseLuma)
        val middle = fingerprint(
            perceptualHash = 3L,
            differenceHash = 3L,
            luminance = IntArray(16 * 16) { baseLuma[it] + 8 }
        )
        val last = fingerprint(
            perceptualHash = 7L,
            differenceHash = 3L,
            luminance = IntArray(16 * 16) { baseLuma[it] + 16 }
        )

        assertTrue(looksLikeSamePicture(first, middle))
        assertTrue(looksLikeSamePicture(middle, last))
        assertFalse(looksLikeSamePicture(first, last))
        assertEquals(listOf(listOf(0, 1)), strictPerceptualGroups(listOf(first, middle, last)))
    }

    @Test
    fun theHashGridSetsABitWhenTheLeftCellIsBrighter() {
        val brighteningRight = IntArray(9 * 8) { index -> (index % 9) * 10 }
        assertEquals(0L, dHashFromLuminanceGrid(brighteningRight))
        val darkeningRight = IntArray(9 * 8) { index -> 80 - (index % 9) * 10 }
        assertEquals(-1L, dHashFromLuminanceGrid(darkeningRight))
        val single = brighteningRight.copyOf()
        single[3 * 9 + 4] = 999
        // Row 3, column 4 -> bit 3*8 + 4.
        assertEquals(1L shl 28, dHashFromLuminanceGrid(single))
    }

    @Test
    fun pHashIgnoresAUniformBrightnessChange() {
        val grid = IntArray(32 * 32) { index ->
            val x = index % 32
            val y = index / 32
            ((x * 7 + y * 11) % 40) * 4
        }
        val brighter = IntArray(grid.size) { (grid[it] + 20).coerceAtMost(255) }
        assertEquals(perceptualHashFromLuminanceGrid(grid), perceptualHashFromLuminanceGrid(brighter))
    }

    @Test
    fun onlyStrictGroupsOfTwoOrMoreSurvive() {
        val same = fingerprint()
        val other = fingerprint(
            perceptualHash = -1L,
            differenceHash = 0L,
            red = 220f,
            green = 20f,
            blue = 20f
        )
        val adjusted = same.copy(
            perceptualHash = same.perceptualHash xor 1L,
            differenceHash = same.differenceHash xor 1L,
            green = 102f
        )
        assertEquals(listOf(listOf(0, 1)), strictPerceptualGroups(listOf(same, adjusted, other)))
        assertTrue(strictPerceptualGroups(listOf(same, other)).isEmpty())
    }

    @Test
    fun savedScanResultsRoundTripAndKeepCompletionState() {
        val saved = DuplicateScanCache(
            groups = listOf(
                SavedDuplicateGroup(
                    hash = "group-a",
                    uris = listOf("content://media/1", "content://media/2")
                )
            ),
            completed = true
        )
        assertEquals(saved, decodeDuplicateScanCache(encodeDuplicateScanCache(saved)))
    }

    @Test
    fun invalidSavedResultsNeverCreateSingleItemGroups() {
        val decoded = decodeDuplicateScanCache(
            """{"completed":true,"groups":[{"hash":"bad","uris":["content://media/1"]}]}"""
        )
        assertEquals(DuplicateScanCache(emptyList(), completed = true), decoded)
        assertEquals(
            DuplicateScanCache(emptyList(), completed = false),
            decodeDuplicateScanCache("not json")
        )
    }

}
