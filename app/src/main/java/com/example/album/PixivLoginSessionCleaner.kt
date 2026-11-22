package com.example.album

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * Pixiv login runs in its own process (`:pixiv_login`) with a dedicated
 * WebView data directory. Cookies cleared from the main process never touch
 * that profile, so logging out has to ask the login process to clear its own
 * cookies, WebStorage and cache.
 *
 * The data directory suffix must be set before any WebView is created in the
 * process; both [PixivWebActivity] and [PixivSessionCleanupService] run inside
 * `:pixiv_login`, so whichever starts first configures it and the other one
 * sees the already-configured flag.
 */
object PixivLoginSessionCleaner {
    const val DATA_DIRECTORY_SUFFIX = "pixiv_login_v2"
    private const val TAG = "PixivSession"
    private const val COOKIE_CALLBACK_TIMEOUT_MS = 1_500L

    @Volatile
    private var suffixConfigured = false

    fun ensureDataDirectorySuffix() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || suffixConfigured) return
        runCatching { WebView.setDataDirectorySuffix(DATA_DIRECTORY_SUFFIX) }
            .onSuccess { suffixConfigured = true }
    }

    /**
     * Clears the WebView state of the calling process. Must run inside
     * `:pixiv_login`; [onDone] fires at most once, after the cookie removal
     * callback or a short timeout, whichever comes first.
     */
    fun clearInCurrentProcess(onDone: (() -> Unit)? = null) {
        ensureDataDirectorySuffix()
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                runCatching { CookieManager.getInstance().flush() }
                runCatching { WebStorage.getInstance().deleteAllData() }
                Log.i(TAG, "Pixiv login WebView data cleared")
                onDone?.invoke()
            }
        }
        runCatching {
            CookieManager.getInstance().removeAllCookies { finish() }
        }.onFailure {
            Log.w(TAG, "Unable to remove Pixiv cookies: ${it.message}")
            finish()
        }
        // The callback can be skipped when the provider is unavailable; never
        // leave the cleanup service running because of that.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, COOKIE_CALLBACK_TIMEOUT_MS)
    }

    /** Asks the `:pixiv_login` process to clear its profile (safe from main). */
    fun requestCleanup(context: Context) {
        runCatching {
            context.startService(Intent(context, PixivSessionCleanupService::class.java))
        }.onFailure {
            Log.w(TAG, "Unable to start Pixiv session cleanup: ${it.message}")
        }
    }
}
