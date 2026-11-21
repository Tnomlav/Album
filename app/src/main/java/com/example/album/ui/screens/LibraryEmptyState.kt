package com.example.album.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    onRetry: () -> Unit
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
            else -> {
                Text(
                    appText(if (isVideo) "还没有视频" else "还没有图片", english),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (english) "Add a folder that Album may read, or rescan shared storage."
                    else "添加一个允许 Album 读取的文件夹，或重新扫描共享存储。",
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
