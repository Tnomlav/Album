package com.example.album.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.album.ui.screens.LibraryErrorBanner
import com.example.album.ui.theme.AlbumivTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** M-11: the persistent error strip must retry and dismiss. */
@RunWith(AndroidJUnit4::class)
class LibraryErrorBannerTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun retryAndDismissCallbacksFire() {
        var retries = 0
        var dismissals = 0
        rule.setContent {
            AlbumivTheme {
                CompositionLocalProvider(LocalAppEnglish provides false) {
                    LibraryErrorBanner(
                        error = "图片读取失败",
                        onRetry = { retries++ },
                        onDismiss = { dismissals++ }
                    )
                }
            }
        }
        rule.onNodeWithText("重试").performClick()
        rule.onNodeWithContentDescription("关闭").performClick()
        assertEquals(1, retries)
        assertEquals(1, dismissals)
    }
}
