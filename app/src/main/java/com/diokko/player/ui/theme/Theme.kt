package com.diokko.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Diokko Player Theme
 * Uses TV Material3 with custom color scheme
 */
@OptIn(ExperimentalTvMaterial3Api::class)
private val DiokkoDarkColorScheme = darkColorScheme(
    primary = DiokkoColors.Accent,
    onPrimary = DiokkoColors.TextOnAccent,
    primaryContainer = DiokkoColors.SurfaceElevated,
    onPrimaryContainer = DiokkoColors.TextPrimary,
    secondary = DiokkoColors.Secondary,
    onSecondary = DiokkoColors.TextPrimary,
    secondaryContainer = DiokkoColors.SurfaceLight,
    onSecondaryContainer = DiokkoColors.TextPrimary,
    tertiary = DiokkoColors.Tertiary,
    onTertiary = DiokkoColors.TextPrimary,
    background = DiokkoColors.Background,
    onBackground = DiokkoColors.TextPrimary,
    surface = DiokkoColors.Surface,
    onSurface = DiokkoColors.TextPrimary,
    surfaceVariant = DiokkoColors.SurfaceLight,
    onSurfaceVariant = DiokkoColors.TextSecondary,
    error = DiokkoColors.Error,
    onError = DiokkoColors.TextOnAccent,
    errorContainer = DiokkoColors.Error.copy(alpha = 0.2f),
    onErrorContainer = DiokkoColors.Error,
    border = DiokkoColors.SurfaceElevated,
    borderVariant = DiokkoColors.FocusBorder
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DiokkoPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DiokkoDarkColorScheme,
        content = content
    )
}
