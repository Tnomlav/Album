package com.example.album.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.album.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A local-only diagnostic snapshot.
 *
 * It deliberately contains no media names, no URIs and no account data: only
 * build/device facts, permission states and app-private storage sizes. The
 * user chooses where to write it through the system file picker.
 */
object DiagnosticsReport {
    @SuppressLint("UsableSpace")
    fun build(context: Context): String = buildString {
        appendLine("Album diagnostics")
        appendLine("generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("package: ${context.packageName}")
        appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("locale: ${Locale.getDefault()}")
        appendLine()
        appendLine("permissions:")
        appendLine("  READ_MEDIA_IMAGES=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) granted(context, android.Manifest.permission.READ_MEDIA_IMAGES) else false}")
        appendLine("  READ_MEDIA_VIDEO=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) granted(context, android.Manifest.permission.READ_MEDIA_VIDEO) else false}")
        appendLine("  READ_MEDIA_VISUAL_USER_SELECTED=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) granted(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else false}")
        appendLine("  READ_EXTERNAL_STORAGE=${granted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)}")
        appendLine("  MANAGE_EXTERNAL_STORAGE=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()}")
        appendLine("  MANAGE_MEDIA=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)}")
        appendLine()
        appendLine("storage:")
        appendLine("  filesDir=${context.filesDir.absolutePath}")
        appendLine("  cacheDir=${context.cacheDir.absolutePath}")
        appendLine("  filesBytes=${directoryBytes(context.filesDir)}")
        appendLine("  cacheBytes=${directoryBytes(context.cacheDir)}")
        appendLine("  freeBytes=${context.filesDir.usableSpace}")
        appendLine()
        appendLine("settings keys: ${context.getSharedPreferences("album_settings", Context.MODE_PRIVATE).all.keys.sorted().joinToString(",")}")
    }

    private fun granted(context: Context, permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun directoryBytes(directory: java.io.File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
