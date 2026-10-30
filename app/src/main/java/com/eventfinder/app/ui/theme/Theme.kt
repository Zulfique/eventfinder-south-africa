package com.eventfinder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EventFinderTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F2E5),
    onPrimaryContainer = Color(0xFF002817),

    secondary = BrandGold,
    onSecondary = Color(0xFF332300),
    secondaryContainer = Color(0xFFFFE8B0),
    onSecondaryContainer = Color(0xFF261A00),

    tertiary = BrandBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E5FF),
    onTertiaryContainer = Color(0xFF00105C),

    background = SurfaceOffWhite,
    onBackground = Color(0xFF17191D),

    surface = BrandWhite,
    onSurface = Color(0xFF17191D),

    surfaceVariant = Color(0xFFE9EDF1),
    onSurfaceVariant = Color(0xFF454B52),

    outline = Color(0xFFD9DEE5),
    outlineVariant = Color(0xFFE7EBF0),

    error = BrandRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF62C99A),
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF005638),
    onPrimaryContainer = Color(0xFFB9F2D3),

    secondary = BrandGold,
    onSecondary = Color(0xFF332300),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE8B0),

    tertiary = Color(0xFFAAB6FF),
    onTertiary = Color(0xFF00105C),
    tertiaryContainer = Color(0xFF182A86),
    onTertiaryContainer = Color(0xFFE0E5FF),

    background = SurfaceInk,
    onBackground = Color(0xFFE5E7EB),

    surface = SurfaceCardDark,
    onSurface = Color(0xFFE5E7EB),

    surfaceVariant = Color(0xFF2A3038),
    onSurfaceVariant = Color(0xFFC2C8D0),

    outline = Color(0xFF46515E),
    outlineVariant = Color(0xFF303B49),

    error = BrandRedGlow,
    onError = Color(0xFF410002)
)

private val EventFinderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
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
            primary = Color.White,
            onPrimary = Color.Black,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF202020),
            onSurfaceVariant = Color.White
        )

        highContrast -> lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFF0F0F0),
            onSurfaceVariant = Color.Black
        )

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = EventFinderTypography,
        shapes = EventFinderShapes,
        content = content
    )
}
