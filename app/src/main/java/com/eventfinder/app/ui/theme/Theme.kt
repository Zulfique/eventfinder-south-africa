package com.eventfinder.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F5EA),
    onPrimaryContainer = Color(0xFF00382E),
    secondary = BrandAmber,
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFFFFE082),
    onSecondaryContainer = Color(0xFF261A00),
    tertiary = BrandPurple,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE7F6),
    onTertiaryContainer = Color(0xFF1A0050),
    background = SurfaceOffWhite,
    surface = SurfaceCard,
    surfaceVariant = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFF444950),
    error = BrandRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenGlow,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = BrandAmberGlow,
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = BrandPurpleGlow,
    onTertiary = Color(0xFF1A0050),
    tertiaryContainer = Color(0xFF4A148C),
    onTertiaryContainer = Color(0xFFEDE7F6),
    background = SurfaceInk,
    surface = SurfaceCardDark,
    surfaceVariant = Color(0xFF2C313A),
    onSurfaceVariant = Color(0xFFC4C9D1),
    error = BrandRedGlow,
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun EventFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        highContrast -> if (darkTheme) darkColorScheme(
            primary = Color.White, onPrimary = Color.Black,
            background = Color.Black, surface = Color.Black,
            onBackground = Color.White, onSurface = Color.White
        ) else lightColorScheme(
            primary = Color.Black, onPrimary = Color.White,
            background = Color.White, surface = Color.White,
            onBackground = Color.Black, onSurface = Color.Black
        )
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> if (darkTheme) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(),
        content = content
    )
}

