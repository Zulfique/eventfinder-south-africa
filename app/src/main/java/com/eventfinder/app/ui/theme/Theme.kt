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
    primary = Color(0xFF00766B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F4EABF),
    onPrimaryContainer = Color(0xFF0B3D2E),
    secondary = Color(0xFFF5B800),
    onSecondary = Color.White,
    tertiary = Color(0xFF1B3A4B),
    background = Color(0xFFF6F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EDF2),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ED7BD),
    onPrimary = Color(0xFF0B3D2E),
    primaryContainer = Color(0xFF0B5C45),
    onPrimaryContainer = Color(0xFFB7F4EABF),
    secondary = Color(0xFFFFCA4D),
    tertiary = Color(0xFF8FB6FF),
    background = Color(0xFF15171B),
    surface = Color(0xFF21262D),
    error = Color(0xFFFF8A80)
)

@Composable
fun EventFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
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

