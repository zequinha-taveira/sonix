package com.vibeflow.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// VibeFlow Custom HSL/Hex Harmonious Color Palette
val VioletPrimary = Color(0xFF8B5CF6) // Vibrant Violet
val CyanSecondary = Color(0xFF06B6D4) // Radiant Cyan
val PinkTertiary = Color(0xFFEC4899)  // Playful Pink

val DarkBackground = Color(0xFF090D16) // Ultra-dark blue-gray background
val DarkSurface = Color(0xFF131B2E)    // Translucent glass base surface
val DarkSurfaceVariant = Color(0xFF1F293D)
val OnDarkBackground = Color(0xFFF3F4F6)
val OnDarkSurface = Color(0xFFE5E7EB)

private val DarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    secondary = CyanSecondary,
    tertiary = PinkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = OnDarkBackground,
    onSurface = OnDarkSurface
)

// We want VibeFlow to remain consistently immersive and dark/frosted,
// so we'll force the dark color scheme to keep that premium music player vibe.
@Composable
fun VibeFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
