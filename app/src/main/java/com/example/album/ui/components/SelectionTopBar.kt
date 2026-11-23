package com.example.album.ui.components

import com.example.album.ui.theme.VaultShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import com.example.album.ui.theme.VaultDimens
import com.example.album.ImageSearchSource
import com.example.album.ui.LocalAppEnglish
import com.example.album.ui.imageSearchActionText
import com.example.album.ui.appText

@Composable
fun SelectionTopBar(
    selected: Int,
    favoriteSelected: Boolean = false,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    renameEnabled: Boolean = true,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSlideshow: (() -> Unit)? = null,
    onAddToSlideshowQueue: (() -> Unit)? = null,
    onOpenWith: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onWallpaper: (() -> Unit)? = null,
    onAddToWallpaperQueue: (() -> Unit)? = null,
    onSearchSauceNao: (() -> Unit)? = null,
    onSearchAscii2d: (() -> Unit)? = null,
    onExclude: (() -> Unit)? = null,
    selectingFolders: Boolean = false,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onClose: () -> Unit,
    chromeAlpha: Float = 1f
) {
    val english = LocalAppEnglish.current
    var menuOpen by remember { mutableStateOf(false) }
    var searchExpanded by remember(selected) { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Surface(modifier = Modifier.alpha(chromeAlpha), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.statusBars)
                // Keep the right action group on the same screen-edge baseline as the normal top bar.
                .height(VaultDimens.HeaderContentHeight).padding(start = 6.dp, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, appText("返回", english))
            }
            if (searchExpanded) {
                Row(
                    modifier = Modifier.weight(1f).height(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, VaultShape.XLarge)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, appText("搜索", english), modifier = Modifier.size(17.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).padding(start = 7.dp)
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (query.isBlank()) Text(
                                    appText(if (selectingFolders) "搜索文件夹" else "搜索图片和视频", english),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                inner()
                            }
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = appText("清除搜索", english), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                IconButton(onClick = { searchExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Search, appText("搜索", english))
                }
                IconButton(onClick = onDelete, enabled = selected > 0, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Delete, appText("删除所选", english)) }
                IconButton(onClick = onMove, enabled = selected > 0, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, appText("移动", english)) }
                IconButton(onClick = onRename, enabled = selected == 1 && renameEnabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.TextFields, appText("重命名", english)) }
                IconButton(onClick = onFavorite, enabled = selected > 0, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (favoriteSelected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        if (favoriteSelected) appText("取消收藏所选", english) else appText("收藏所选", english),
                        tint = if (favoriteSelected) androidx.compose.ui.graphics.Color(0xFFFFD60A) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuOpen = true }, enabled = selected > 0, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.MoreVert, appText("更多操作", english)) }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(190.dp).clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                ) {
                    fun run(action: () -> Unit) { menuOpen = false; action() }
                    DropdownMenuItem(text = { Text(appText("分享", english)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { run(onShare) })
                    DropdownMenuItem(text = { Text(appText("复制", english)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { run(onCopy) })
                    if (searchExpanded) {
                        DropdownMenuItem(text = { Text(appText("移动", english)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) }, enabled = selected > 0, onClick = { run(onMove) })
                        DropdownMenuItem(text = { Text(appText("重命名", english)) }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) }, enabled = selected == 1 && renameEnabled, onClick = { run(onRename) })
                    }
                    onEdit?.let { action -> DropdownMenuItem(text = { Text(appText("编辑", english)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { run(action) }) }
                    onEditTags?.let { action -> DropdownMenuItem(text = { Text(if (english) "View/Edit Tags" else "查看/编辑 Tags") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, onClick = { run(action) }) }
                    if (searchExpanded) {
                        DropdownMenuItem(
                            text = { Text(if (favoriteSelected) appText("取消收藏所选", english) else appText("收藏所选", english)) },
                            leadingIcon = { Icon(if (favoriteSelected) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                            enabled = selected > 0,
                            onClick = { run(onFavorite) }
                        )
                    }
                    onOpenWith?.let { action -> DropdownMenuItem(text = { Text(appText("打开方式", english)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) }, onClick = { run(action) }) }
                    onInfo?.let { action -> DropdownMenuItem(text = { Text(appText("信息", english)) }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { run(action) }) }
                    onSlideshow?.let { action -> DropdownMenuItem(text = { Text(appText("幻灯片", english)) }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) }, onClick = { run(action) }) }
                    onAddToSlideshowQueue?.let { action -> DropdownMenuItem(text = { Text(appText("加入幻灯片队列", english)) }, leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) }, onClick = { run(action) }) }
                    onWallpaper?.let { action -> DropdownMenuItem(text = { Text(appText("设置为壁纸", english)) }, leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) }, onClick = { run(action) }) }
                    onAddToWallpaperQueue?.let { action -> DropdownMenuItem(text = { Text(appText("加入壁纸队列", english)) }, leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) }, onClick = { run(action) }) }
                    onSearchSauceNao?.let { action ->
                        DropdownMenuItem(
                            text = { Text(imageSearchActionText(ImageSearchSource.SauceNao, english)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            onClick = { run(action) }
                        )
                    }
                    onSearchAscii2d?.let { action ->
                        DropdownMenuItem(
                            text = { Text(imageSearchActionText(ImageSearchSource.Ascii2d, english)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            onClick = { run(action) }
                        )
                    }
                    onExclude?.let { action -> DropdownMenuItem(text = { Text(appText("排除", english)) }, leadingIcon = { Icon(Icons.Outlined.FolderOff, null) }, onClick = { run(action) }) }
                    if (searchExpanded) {
                        DropdownMenuItem(text = { Text(appText("删除所选", english)) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, enabled = selected > 0, onClick = { run(onDelete) })
                    }
                }
            }
        }
    }
}

/**
 * Count plus a 48dp "select all" for pages whose selection lives in a top bar
 * (the cleanup pages). It is deliberately not a second action strip: the only
 * actions here are the count and the bulk toggle.
 */
@Composable
fun SelectionCountBar(
    selected: Int,
    total: Int,
    onSelectAll: () -> Unit,
    chromeAlpha: Float = 1f
) {
    val english = LocalAppEnglish.current
    val allSelected = total > 0 && selected == total
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(chromeAlpha),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selected.toString() + " / " + total.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSelectAll, modifier = Modifier.height(48.dp)) {
                Text(appText(if (allSelected) "取消全选" else "全选", english))
            }
        }
    }
}
