package com.eventfinder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F3DF),
    onPrimaryContainer = Color(0xFF0B3D2E),
    secondary = Amber,
    onSecondary = Color.White,
    tertiary = Navy,
    background = OffWhite,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF0),
    error = Red
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3ED9AE),
    onPrimary = Color(0xFF0B3D2E),
    primaryContainer = Color(0xFF0B5C45),
    onPrimaryContainer = Color(0xFFB6F3DF),
    secondary = Color(0xFFFFCA3D),
    tertiary = Color(0xFF8FB6FF),
    background = DarkSurface,
    surface = DarkSurfaceHigh,
    error = Color(0xFFFF8A80)
)

/**
 * Material 3 theme. Supports light/dark following the system setting.
 */
@Composable
fun EventFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}