package com.example.album

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSearchSourceTest {
    @Test
    fun sourceKeysRoundTrip() {
        ImageSearchSource.entries.forEach { source ->
            assertEquals(source, ImageSearchSource.fromKey(source.key))
        }
        assertEquals(ImageSearchSource.SauceNao, ImageSearchSource.fromKey(null))
    }

    @Test
    fun eachSourceOnlyClaimsItsOwnHttpsDomain() {
        assertTrue(ImageSearchSource.SauceNao.allowsHost("saucenao.com"))
        assertTrue(ImageSearchSource.SauceNao.allowsHost("WWW.SAUCENAO.COM"))
        assertFalse(ImageSearchSource.SauceNao.allowsHost("saucenao.com.evil.example"))
        assertFalse(ImageSearchSource.SauceNao.allowsHost("ascii2d.net"))

        assertTrue(ImageSearchSource.Ascii2d.allowsHost("ascii2d.net"))
        assertFalse(ImageSearchSource.Ascii2d.allowsHost("ascii2d.net.evil.example"))
        assertFalse(ImageSearchSource.Ascii2d.allowsHost("saucenao.com"))
        assertFalse(ImageSearchSource.Ascii2d.allowsHost(null))
    }

    @Test
    fun shareSheetAliasesPickTheirOwnSite() {
        assertEquals(
            ImageSearchSource.SauceNao,
            ImageSearchSource.fromShareComponent("com.example.album.ImageSearchSauceNao")
        )
        assertEquals(
            ImageSearchSource.Ascii2d,
            ImageSearchSource.fromShareComponent("com.example.album.ImageSearchAscii2d")
        )
        // Anything that is not one of the share aliases keeps the explicit extra.
        assertNull(ImageSearchSource.fromShareComponent("com.example.album.MainActivity"))
        assertNull(ImageSearchSource.fromShareComponent(null))
    }
}
