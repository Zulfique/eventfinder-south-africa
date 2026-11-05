package com.eventfinder.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour for small accent text and glyphs sitting on a surface.
 *
 * The brand emerald (#00C98D) reads well on the night background but only
 * reaches ~1.9:1 on white, so light mode keeps the deeper #007749 for text
 * while buttons and other filled controls use [ColorScheme.primary] as-is.
 */
val LocalAccentText = staticCompositionLocalOf { BrandGreen }

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
    // Same emerald as dark mode so filled buttons, chips and switches look
    // identical across themes; small text uses [LocalAccentText] instead.
    primary = Color(0xFF00C98D),
    onPrimary = Color(0xFF003820),
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
    // Showcase dark-mode accent: bright emerald green.
    primary = Color(0xFF00C98D),
    onPrimary = Color(0xFF003820),
    // 2026 palette: "primary dark" #00906B for contained accents.
    primaryContainer = Color(0xFF00906B),
    onPrimaryContainer = Color(0xFFFFFFFF),

    secondary = BrandGold,
    onSecondary = Color(0xFF332300),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE8B0),

    tertiary = Color(0xFFAAB6FF),
    onTertiary = Color(0xFF00105C),
    tertiaryContainer = Color(0xFF182A86),
    onTertiaryContainer = Color(0xFFE0E5FF),

    // 2026 dark palette: #0B1929 background, #13293D surface, #1E2A44 raised
    // cards, #203852 borders and the blue-grey secondary text.
    background = Color(0xFF0B1929),
    onBackground = Color(0xFFFFFFFF),

    surface = Color(0xFF13293D),
    onSurface = Color(0xFFFFFFFF),

    surfaceVariant = Color(0xFF1E2A44),
    onSurfaceVariant = Color(0xFFA8B5D3),

    outline = Color(0xFF203852),
    outlineVariant = Color(0xFF1B2C3F),

    error = Color(0xFFFF4444),
    onError = Color(0xFFFFFFFF)
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

        // Material You dynamic colour on Android 12+, keeping the brand night
        // background (#0B1929) as specified for dark mode.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
                    .copy(background = Color(0xFF0B1929))
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    val accentText = when {
        highContrast && darkTheme -> Color.White
        highContrast -> Color.Black
        darkTheme -> Color(0xFF00C98D)
        else -> BrandGreen
    }

    CompositionLocalProvider(LocalAccentText provides accentText) {
        MaterialTheme(
            colorScheme = colors,
            typography = EventFinderTypography,
            shapes = EventFinderShapes,
            content = content
        )
    }
}
