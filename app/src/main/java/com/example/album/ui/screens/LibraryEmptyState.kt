package com.example.album.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.WarningAmber
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText

/**
 * The shared empty/error state for the two media pages.
 *
 * A failed scan used to look exactly like an empty library. The error branch
 * keeps the retry action visible instead of relying on a Toast that disappears.
 */
@Composable
fun LibraryEmptyState(
    isVideo: Boolean,
    query: String,
    error: String?,
    onClearQuery: () -> Unit,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
    onRetry: () -> Unit,
    favoritesOnly: Boolean = false,
    onShowAll: () -> Unit = {}
) {
    val english = LocalAppEnglish.current
    val searching = query.isNotBlank()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            searching -> {
                Text(
                    appText("没有匹配的内容", english),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onClearQuery, modifier = Modifier.padding(top = 8.dp)) {
                    Text(appText("清除搜索", english))
                }
            }
            error != null -> {
                Text(
                    if (english) "Some media could not be read" else "部分媒体读取失败",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (english) "Retry the scan. If it still fails, check storage space and media permissions."
                    else "请重试扫描；如果仍然失败，请检查存储空间和媒体权限。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text(appText("重试", english))
                }
            }
            favoritesOnly -> {
                Text(
                    appText("还没有收藏", english),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (english) "Tap the star on any item to add it here."
                    else "在任意图片或视频上点星标，收藏就会出现在这里。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onShowAll, modifier = Modifier.padding(top = 8.dp)) {
                    Text(appText("查看全部", english))
                }
            }
            else -> {
                Text(
                    appText(if (isVideo) "还没有视频" else "还没有图片", english),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (english) "Add a folder that Albumiv may read, or rescan shared storage."
                    else "添加一个允许 Albumiv 读取的文件夹，或重新扫描共享存储。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onAddFolder, modifier = Modifier.padding(top = 8.dp)) {
                    Text(appText("添加本地文件夹", english))
                }
                TextButton(onClick = onRescan) {
                    Text(appText("重新扫描", english))
                }
            }
        }
    }
}


/** Persistent, dismissible scan error shown above a non-empty list. */
@Composable
fun LibraryErrorBanner(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val english = LocalAppEnglish.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.width(20.dp))
            Text(
                error,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            TextButton(onClick = onRetry) { Text(if (english) "Retry" else "重试") }
            IconButton(onClick = onDismiss, modifier = Modifier.width(36.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = if (english) "Dismiss" else "关闭")
            }
        }
    }
}


/** Android 14+ partial photo/video access: explain why the library looks small. */
@Composable
fun PartialAccessBanner(
    onSelectMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val english = LocalAppEnglish.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (english) "Only selected photos and videos are available" else "当前只能访问你选中的照片和视频",
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            TextButton(onClick = onSelectMore) {
                Text(if (english) "Select more" else "选择更多")
            }
        }
    }
}
