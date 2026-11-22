package com.example.album

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs inside `:pixiv_login` so it can clear that process's WebView profile.
 * Started from the main process when the user logs out or switches accounts.
 */
class PixivSessionCleanupService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stopped = AtomicBoolean(false)
        PixivLoginSessionCleaner.clearInCurrentProcess {
            if (stopped.compareAndSet(false, true)) stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
