package com.example.album.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat

fun resistedPullDistance(current: Float, delta: Float, maxDistance: Float): Float {
    if (delta < 0f) return (current + delta).coerceAtLeast(0f)
    if (delta == 0f) return current
    val resistance = 1f + current / maxDistance.coerceAtLeast(1f)
    return (current + delta / resistance).coerceAtMost(maxDistance)
}

@Composable
fun rememberPullRefreshConnection(
    enabled: Boolean,
    refreshing: Boolean,
    atTop: () -> Boolean,
    pullDistance: () -> Float,
    triggerDistance: Float,
    maxDistance: Float,
    onPullDistanceChange: (Float) -> Unit,
    onRelease: () -> Unit,
    onRefreshStarted: () -> Unit = {}
): NestedScrollConnection {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestRefreshing by rememberUpdatedState(refreshing)
    val latestAtTop by rememberUpdatedState(atTop)
    val latestPullProvider by rememberUpdatedState(pullDistance)
    val latestTrigger by rememberUpdatedState(triggerDistance)
    val latestMaxDistance by rememberUpdatedState(maxDistance)
    val latestPullHandler by rememberUpdatedState(onPullDistanceChange)
    val latestRelease by rememberUpdatedState(onRelease)
    val latestStarted by rememberUpdatedState(onRefreshStarted)
    return androidx.compose.runtime.remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!latestEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                val pull = latestPullProvider()
                if (!latestRefreshing && delta > 0f && (pull > 0f || latestAtTop())) {
                    latestPullHandler(resistedPullDistance(pull, delta, latestMaxDistance))
                    return Offset(0f, delta)
                }
                if (!latestRefreshing && delta < 0f && pull > 0f) {
                    val newPull = (pull + delta).coerceAtLeast(0f)
                    val consumed = newPull - pull
                    latestPullHandler(newPull)
                    return Offset(0f, consumed)
                }
                if (latestRefreshing && pull > 0f && delta > 0f) {
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pull = latestPullProvider()
                if (latestEnabled && pull > 0f) {
                    if (!latestRefreshing && pull >= latestTrigger) latestStarted()
                    else if (!latestRefreshing) latestRelease()
                    return available
                }
                return Velocity.Zero
            }
        }
    }
}

/**
 * Everything a scrollable page needs for pull-to-refresh: the distance to feed
 * the indicator, the offset to shift the content by, and the scroll connection.
 *
 * The grid pages used to spell this out five times with the same numbers and
 * the same "loading alone must not move the content" rule.
 */
class PullToRefresh(
    val distance: Float,
    val offset: Float,
    val triggerDistance: Float,
    val connection: NestedScrollConnection,
    /**
     * True while the user's pull is being served, including the moment it is
     * queued behind a scan that is already running. The indicator must spin for
     * that whole time, otherwise a queued pull looks frozen.
     */
    val spinning: Boolean
)

/**
 * Keeps track of which pull-to-refresh request has already been played.
 *
 * The request travels as a token from the bottom bar down to whichever grid is
 * on screen. Without this, every grid that composed later saw the last token
 * again and replayed the pull — opening or closing any page triggered a refresh.
 */
internal object PullRefreshRequests {
    @Volatile
    private var handled = 0L

    fun claim(token: Long): Boolean {
        if (token <= 0L) return false
        synchronized(this) {
            if (token == handled) return false
            handled = token
            return true
        }
    }
}

@Composable
fun rememberPullToRefresh(
    refreshing: Boolean,
    enabled: Boolean,
    atTop: () -> Boolean,
    onRefresh: () -> Unit,
    label: String,
    /**
     * Bumped by the caller to ask for a refresh that looks like a pull: the
     * indicator is dragged down to the trigger distance and only then does the
     * reload start. Tapping the current tab in the bottom bar uses this.
     */
    requestToken: Long = 0L
): PullToRefresh {
    var distance by androidx.compose.runtime.remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // A shorter throw than before: the old 96dp trigger with the same
    // resistance felt like it needed two swipes to arm.
    val trigger = with(density) { 72.dp.toPx() }
    val maxPull = with(density) { 120.dp.toPx() }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var armed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(distance, enabled, refreshing) {
        if (!enabled || refreshing) {
            armed = false
            return@LaunchedEffect
        }
        val nowArmed = distance >= trigger
        if (nowArmed && !armed) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
        armed = nowArmed
    }
    // A pull asks the library to reload; the scan may have to wait for one
    // that is already running, so the indicator stays armed until it actually
    // starts instead of snapping back for a moment.
    var awaitingStart by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Loading/scanning alone must not move the content like a pull gesture.
    val pullRefreshing = (refreshing || awaitingStart) && distance > 0f
    androidx.compose.runtime.LaunchedEffect(refreshing) {
        if (refreshing) {
            awaitingStart = false
        } else if (!awaitingStart) {
            distance = 0f
        }
    }
    androidx.compose.runtime.LaunchedEffect(awaitingStart) {
        if (awaitingStart) {
            // A fast scan can start and finish between two frames, so the UI may
            // never observe `refreshing` turning true. Without this the content
            // would stay pulled down with a frozen arc forever.
            kotlinx.coroutines.delay(1500L)
            if (awaitingStart) {
                awaitingStart = false
                distance = 0f
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(requestToken) {
        if (requestToken <= 0L || !enabled || !atTop()) return@LaunchedEffect
        // A request is a one-shot signal: a grid that composes later (a folder
        // being opened, a page being switched) must not replay it.
        if (!PullRefreshRequests.claim(requestToken)) return@LaunchedEffect
        val animation = androidx.compose.animation.core.Animatable(distance)
        animation.animateTo(
            trigger,
            tween(220, easing = CubicBezierEasing(.22f, .8f, .28f, 1f))
        ) { distance = value }
        awaitingStart = true
        onRefresh()
    }
    val offset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pullRefreshing) trigger else distance,
        animationSpec = if (distance > 0f && !refreshing) {
            androidx.compose.animation.core.snap()
        } else {
            tween(240, easing = CubicBezierEasing(.22f, .8f, .28f, 1f))
        },
        label = label
    )
    val connection = rememberPullRefreshConnection(
        enabled = enabled,
        refreshing = refreshing,
        atTop = atTop,
        pullDistance = { distance },
        triggerDistance = trigger,
        maxDistance = maxPull,
        onPullDistanceChange = { distance = it },
        onRelease = { distance = 0f },
        onRefreshStarted = {
            distance = trigger
            awaitingStart = true
            onRefresh()
        }
    )
    return PullToRefresh(distance, offset, trigger, connection, pullRefreshing)
}

@Composable
fun PullRefreshIndicator(
    pullDistance: Float,
    refreshing: Boolean,
    triggerDistance: Float,
    modifier: Modifier = Modifier
) {
    val progress = (pullDistance / triggerDistance.coerceAtLeast(1f)).coerceIn(0f, 1f)
    // The spin is driven by the composition's frame clock directly. An infinite
    // transition looked frozen on devices where the first frames after the
    // refresh started were dropped, and this keeps the angle advancing.
    var rotation by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(refreshing) {
        if (!refreshing) {
            rotation = 0f
            return@LaunchedEffect
        }
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val deltaMs = (now - last) / 1_000_000f
                last = now
                rotation = (rotation + deltaMs / 900f * 360f) % 360f
            }
        }
    }
    AnimatedVisibility(
        visible = refreshing || progress > .02f,
        modifier = modifier.fillMaxWidth().zIndex(21f),
        enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = .72f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = .72f)
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val accent = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(22.dp)) {
                        drawArc(
                            Color.Gray.copy(alpha = .30f),
                            0f,
                            360f,
                            false,
                            style = Stroke(2.dp.toPx())
                        )
                        // While dragging the arc grows and turns with the pull;
                        // once refreshing it becomes a steady spinner.
                        val start = if (refreshing) rotation - 90f else -90f + progress * 120f
                        val sweep = if (refreshing) 100f else 36f + progress * 264f
                        drawArc(
                            accent,
                            start,
                            sweep,
                            false,
                            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
