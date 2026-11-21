package com.example.album.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import com.example.album.data.MediaItem
import com.example.album.data.folderKey
import com.example.album.ui.components.SelectionSubBar
import com.example.album.ui.theme.VaultDimens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The bottom bar: selection strip plus the reorderable tab row.
 *
 * Lifted out of `AlbumivApp` together with its drag state (dragged tab, drag
 * position, click suppression, measured width). The caller keeps only the
 * behaviour that depends on the page: selecting a tab, re-tapping the current
 * tab, and persisting a new order.
 */
@Composable
fun AppBottomBar(
    selectedTab: MainTab,
    tabs: List<MainTab>,
    tabOrder: List<MainTab>,
    navReorderEnabled: Boolean,
    selectionMode: Boolean,
    selectingFolders: Boolean,
    selectedFolders: Set<String>,
    selectedUris: Set<String>,
    selectedItemsForAction: List<MediaItem>,
    selectionMedia: List<MediaItem>,
    chromeAlpha: Float,
    barVisible: Boolean,
    icon: (MainTab) -> ImageVector,
    label: (MainTab) -> String,
    onSelectTab: (MainTab) -> Unit,
    onReselectTab: (MainTab) -> Unit,
    onReorder: (List<MainTab>) -> Unit,
    onSelectedFoldersChange: (Set<String>) -> Unit,
    onSelectedUrisChange: (Set<String>) -> Unit
) {
    var bottomBarWidth by remember { mutableIntStateOf(0) }
    var draggedNavTab by remember { mutableStateOf<MainTab?>(null) }
    var navDragX by remember { mutableFloatStateOf(0f) }
    var navDragY by remember { mutableFloatStateOf(0f) }
    var suppressNavClickUntil by remember { mutableLongStateOf(0L) }
    val tabOrderState by rememberUpdatedState(tabOrder)
    val hapticFeedback = LocalHapticFeedback.current
    val english = LocalAppEnglish.current

    Column(Modifier.fillMaxWidth()) {
        if (selectionMode) SelectionSubBar(
            selected = if (selectingFolders) selectedFolders.size else selectedItemsForAction.size,
            total = if (selectingFolders) selectionMedia.map { it.folder }.distinct().size else selectionMedia.size,
            onSelectAll = {
                if (selectingFolders) {
                    val all = selectionMedia.mapTo(mutableSetOf()) { it.folderKey() }
                    onSelectedFoldersChange(
                        if (all.isNotEmpty() && all.all { it in selectedFolders }) selectedFolders - all
                        else selectedFolders + all
                    )
                } else {
                    val visibleKeys = selectionMedia.mapTo(mutableSetOf()) { it.uri.toString() }
                    onSelectedUrisChange(
                        if (visibleKeys.isNotEmpty() && visibleKeys.all { it in selectedUris }) selectedUris - visibleKeys
                        else selectedUris + visibleKeys
                    )
                }
            },
            chromeAlpha = chromeAlpha
        )
    AnimatedVisibility(
        visible = barVisible,
        enter = slideInVertically(tween(170, easing = CubicBezierEasing(.22f, .8f, .28f, 1f))) { it } + fadeIn(tween(120)),
        // Leaving the page hides the bar at once: the slide-out used to
        // still be running while the folder page was already visible.
        exit = ExitTransition.None
    ) {
    Surface(
        modifier = Modifier.alpha(chromeAlpha),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(VaultDimens.BottomBarHeight)
                .onSizeChanged { bottomBarWidth = it.width }
        ) {
        val bottomTabs = tabs
        val bottomTabWidth = bottomBarWidth.toFloat() / tabs.size.coerceAtLeast(1)
        val navDragThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
        tabs.forEach { tab -> key(tab) {
            val selected = selectedTab == tab
            val slot = tabOrder.indexOf(tab).coerceAtLeast(0)
            val slotX by animateFloatAsState(
                targetValue = slot * bottomTabWidth,
                animationSpec = tween(180, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)),
                label = "nav-slot-${tab.name}"
            )
            val sourceAlpha by animateFloatAsState(
                targetValue = if (draggedNavTab == tab) .25f else 1f,
                animationSpec = tween(120),
                label = "nav-drag-alpha-${tab.name}"
            )
            val navTint by animateColorAsState(
                if (selected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                tween(180),
                label = "nav-tint-${tab.name}"
            )
            val iconOffset by animateDpAsState(if (selected) 0.dp else 6.dp, tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f)), label = "nav-offset-${tab.name}")
            val labelAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(140), label = "nav-label-${tab.name}")
            Column(
                modifier = Modifier.fillMaxWidth(1f / tabs.size).fillMaxHeight()
                    .graphicsLayer {
                        translationX = slotX
                        alpha = sourceAlpha
                    }
                    .pointerInput(tab, navReorderEnabled, bottomTabWidth) {
                        if (!navReorderEnabled || bottomTabWidth <= 0) return@pointerInput
                        var horizontalDragDistance = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                val index = tabOrderState.indexOf(tab).coerceAtLeast(0)
                                draggedNavTab = tab
                                navDragX = index * bottomTabWidth + position.x
                                navDragY = position.y
                                horizontalDragDistance = 0f
                                suppressNavClickUntil = android.os.SystemClock.elapsedRealtime() + 400L
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                horizontalDragDistance += kotlin.math.abs(amount.x)
                                navDragX = (navDragX + amount.x).coerceIn(0f, bottomBarWidth.toFloat())
                                navDragY += amount.y
                                // Do not reorder on the small involuntary movement that often
                                // follows a long press. Reordering starts only after a real drag.
                                if (horizontalDragDistance < navDragThresholdPx) return@detectDragGesturesAfterLongPress
                                val order = tabOrderState
                                val from = order.indexOf(tab)
                                val to = (navDragX / bottomTabWidth).toInt().coerceIn(order.indices)
                                if (from >= 0 && from != to) {
                                    val reordered = order.toMutableList().apply {
                                        val moved = removeAt(from)
                                        add(to, moved)
                                    }
                                     onReorder(reordered)
                                     
                                 }
                             },
                            onDragEnd = { draggedNavTab = null },
                            onDragCancel = { draggedNavTab = null }
                        )
                    }
                    .semantics {
                        stateDescription = if (selected) {
                            if (english) "Selected" else "已选中"
                        } else {
                            if (english) "Not selected" else "未选中"
                        }
                    }
                    .clickable(
                        interactionSource = remember(tab) { MutableInteractionSource() },
                        indication = null
                    ) {
                    if (android.os.SystemClock.elapsedRealtime() < suppressNavClickUntil) return@clickable
                    if (tab != selectedTab) {
                        onSelectTab(tab)
                        return@clickable
                    }
                    onReselectTab(tab)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                if (tab == MainTab.Pixiv) {
                    Box(
                        modifier = Modifier.height(24.dp).width(24.dp).offset { IntOffset(0, iconOffset.roundToPx()) }.background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon(tab),
                            contentDescription = label(tab),
                            tint = navTint,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = 1.45f
                                scaleY = 1.45f
                            }
                        )
                    }
                } else {
                    Icon(
                        icon(tab),
                        contentDescription = label(tab),
                        tint = navTint,
                        modifier = Modifier.height(24.dp).offset { IntOffset(0, iconOffset.roundToPx()) }
                    )
                }
                Text(
                    label(tab),
                    color = navTint,
                    fontSize = VaultDimens.BottomLabel,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp).alpha(labelAlpha)
                )
            }
        } }
        draggedNavTab?.let { dragged ->
            val ghostWidth = 72.dp
            val ghostHeight = 56.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            val ghostWidthPx = with(density) { ghostWidth.toPx() }
            val ghostHeightPx = with(density) { ghostHeight.toPx() }
            val ghostSelected = selectedTab == dragged
            val ghostTint = if (ghostSelected) {
                androidx.compose.material3.MaterialTheme.colorScheme.primary
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (navDragX - ghostWidthPx / 2f).roundToInt(),
                            (navDragY - ghostHeightPx / 2f).roundToInt()
                        )
                    }
                    .width(ghostWidth)
                    .height(ghostHeight)
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .zIndex(10f),
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                shadowElevation = 6.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(dragged.icon, contentDescription = null, tint = ghostTint, modifier = Modifier.height(25.dp))
                    Text(label(dragged), color = ghostTint, fontSize = VaultDimens.BottomLabel, maxLines = 1)
                }
            }
        }
        }
    }
    }
    }
}

