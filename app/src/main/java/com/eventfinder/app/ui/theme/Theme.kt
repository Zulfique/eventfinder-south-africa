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
    primary = BrandGreen, onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F2E5), onPrimaryContainer = Color(0xFF002817),
    secondary = BrandGold, onSecondary = Color(0xFF332300),
    secondaryContainer = Color(0xFFFFE8B0), onSecondaryContainer = Color(0xFF261A00),
    tertiary = BrandBlue, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E5FF), onTertiaryContainer = Color(0xFF00105C),
    background = SurfaceOffWhite, onBackground = Color(0xFF17191D),
    surface = BrandWhite, onSurface = Color(0xFF17191D),
    surfaceVariant = Color(0xFFE9EDF1), onSurfaceVariant = Color(0xFF454B52),
    error = BrandRed, onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF62C99A), onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF005638), onPrimaryContainer = Color(0xFFB9F2D3),
    secondary = BrandGold, onSecondary = Color(0xFF332300),
    secondaryContainer = Color(0xFF5C4300), onSecondaryContainer = Color(0xFFFFE8B0),
    tertiary = Color(0xFFAAB6FF), onTertiary = Color(0xFF00105C),
    tertiaryContainer = Color(0xFF182A86), onTertiaryContainer = Color(0xFFE0E5FF),
    background = SurfaceInk, onBackground = Color(0xFFE5E7EB),
    surface = SurfaceCardDark, onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF2A3038), onSurfaceVariant = Color(0xFFC2C8D0),
    error = BrandRedGlow, onError = Color(0xFF410002)
)

@Composable
fun EventFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        highContrast && darkTheme -> darkColorScheme(
            primary = Color.White, onPrimary = Color.Black,
            background = Color.Black, onBackground = Color.White,
            surface = Color.Black, onSurface = Color.White
        )
        highContrast -> lightColorScheme(
            primary = Color.Black, onPrimary = Color.White,
            background = Color.White, onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black
        )
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), shapes = Shapes(), content = content)
}
