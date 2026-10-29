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
    onSecondary = Color(0xFF2E2100),
    // Material 3's baseline containers are lavender/purple. Leaving them
    // unset made every category badge and tonal button render purple in an
    // otherwise green app, so they are pinned to the brand palette here.
    secondaryContainer = Color(0xFFFFE7B8),
    onSecondaryContainer = Color(0xFF4A3300),
    tertiary = Navy,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE4F2),
    onTertiaryContainer = Color(0xFF12294A),
    background = OffWhite,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF0),
    error = Red,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3ED9AE),
    onPrimary = Color(0xFF0B3D2E),
    primaryContainer = Color(0xFF0B5C45),
    onPrimaryContainer = Color(0xFFB6F3DF),
    secondary = Color(0xFFFFCA3D),
    onSecondary = Color(0xFF2E2100),
    secondaryContainer = Color(0xFF5C4400),
    onSecondaryContainer = Color(0xFFFFE7B8),
    tertiary = Color(0xFF8FB6FF),
    onTertiary = Color(0xFF0C1B33),
    tertiaryContainer = Color(0xFF2C3E5C),
    onTertiaryContainer = Color(0xFFDCE4F2),
    background = DarkSurface,
    surface = DarkSurfaceHigh,
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
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