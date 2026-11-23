package com.example.album.ui.components

import com.example.album.ui.theme.VaultShape

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder grid shown while the first library scan runs.
 *
 * The pages used to render a blank white area here, which read as "the app is
 * empty" for the second or two a cold start takes on a large library.
 */
@Composable
internal fun LibrarySkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rows: Int = 4,
    gap: Dp = 15.dp
) {
    val transition = rememberInfiniteTransition(label = "library-skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "library-skeleton-pulse"
    )
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(columns) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(VaultShape.Medium)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = pulse))
                    )
                }
            }
        }
        // A couple of text bars under the first row so the placeholder reads as
        // a library grid instead of a plain mosaic.
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(columns) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = pulse))
                )
            }
        }
    }
}
