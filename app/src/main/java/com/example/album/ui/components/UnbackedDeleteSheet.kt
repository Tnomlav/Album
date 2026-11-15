package com.example.album.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.MediaItem
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.appText

/**
 * Shown when the private recycle bin could not take some files, usually because
 * the data partition is full. Instead of one all-or-nothing question, every
 * affected item can be ticked individually: ticked ones are deleted for good,
 * the rest stay in the library untouched.
 */
@Composable
internal fun UnbackedDeleteSheet(
    items: List<MediaItem>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val english = LocalAppEnglish.current
    VaultBottomSheet(
        title = appText("无法移到回收站", english),
        onDismiss = onDismiss
    ) {
        Text(
            appText(
                "以下项目无法复制到回收站（通常是可用空间不足）。勾选要永久删除的，其余会保留。",
                english
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { item ->
                val key = item.uri.toString()
                val checked = key in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(key) },
                        modifier = Modifier.size(40.dp)
                    )
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(
                            item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            item.folder + " · " + sizeLabel(item.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Text(
            if (english) selected.size.toString() + " selected" else "已选 " + selected.size + " 项",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VaultSheetConfirmButtons(
            confirmLabel = appText("永久删除所选", english),
            onConfirm = onDeleteSelected,
            onDismiss = onDismiss,
            danger = true,
            confirmEnabled = selected.isNotEmpty()
        )
    }
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
    else -> bytes.toString() + " B"
}
