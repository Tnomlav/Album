package com.example.album.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private fun darkScheme(
    accent: Color,
    onAccent: Color,
    background: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    background = background,
    surface = surface,
    onBackground = onSurface,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    // Material's default here is a light grey-purple that turns every card into
    // a bright block. A surface one step above the vault background keeps the
    // same contrast order as the light theme (page < card < text).
    surfaceVariant = VaultDarkRaised,
    surfaceContainer = VaultDarkSurface,
    surfaceContainerLow = VaultDarkBackground,
    surfaceContainerHigh = VaultDarkRaised,
    surfaceContainerHighest = VaultDarkRaised,
    outline = VaultDarkLine
)

private fun lightScheme(
    accent: Color,
    onAccent: Color,
    background: Color,
    surface: Color,
    onSurface: Color,
    onSurfaceVariant: Color
) = lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    background = background,
    surface = surface,
    onBackground = onSurface,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    surfaceVariant = VaultSecondarySurface,
    surfaceContainer = VaultSurface,
    surfaceContainerLow = VaultSurface,
    surfaceContainerHigh = VaultSurface,
    surfaceContainerHighest = VaultSecondarySurface,
    outline = VaultLine
)

@Composable
fun AlbumivTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: ThemeAccent = ThemeAccent.Blue,
    content: @Composable () -> Unit
) {
    val duration = 200
    val palette = accent.palette
    val targetPrimary = if (darkTheme) palette.dark else palette.light
    val targetOnPrimary = if (darkTheme) palette.onDark else palette.onLight
    val animatedAccent = animateColorAsState(targetPrimary, tween(duration), label = "theme-accent").value
    val animatedOnAccent = animateColorAsState(targetOnPrimary, tween(duration), label = "theme-on-accent").value
    val background = animateColorAsState(if (darkTheme) VaultDarkBackground else VaultBackground, tween(duration), label = "theme-background").value
    val surface = animateColorAsState(if (darkTheme) VaultDarkSurface else VaultSurface, tween(duration), label = "theme-surface").value
    val onSurface = animateColorAsState(if (darkTheme) Color(0xFFECEFEC) else VaultInk, tween(duration), label = "theme-on-surface").value
    val onSurfaceVariant = animateColorAsState(if (darkTheme) Color(0xFFA6AEA9) else VaultMuted, tween(duration), label = "theme-muted").value
    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkScheme(animatedAccent, animatedOnAccent, background, surface, onSurface, onSurfaceVariant)
        } else {
            lightScheme(animatedAccent, animatedOnAccent, background, surface, onSurface, onSurfaceVariant)
        },
        typography = Typography
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
    }
}

/**
 * True when the app is rendering its dark theme. Full-screen surfaces use this
 * instead of isSystemInDarkTheme() because the in-app theme can be forced
 * independently of the system setting.
 */
@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
