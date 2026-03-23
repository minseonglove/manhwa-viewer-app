package com.manhwaviewer.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Design tokens from manga-viewer-all.jsx
object MangaLensColors {
    val primary = Color(0xFF4A90D9)
    val primaryVariant = Color(0xFF2F6EAF)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFF1C3A5C)
    val onPrimaryContainer = Color(0xFFD0E8FF)
    val surface = Color(0xFF111118)
    val surfaceVariant = Color(0xFF1C1C26)
    val surfaceContainer = Color(0xFF22222E)
    val onSurface = Color(0xFFE8E8F0)
    val onSurfaceVariant = Color(0xFFA0A0B8)
    val error = Color(0xFFCF6679)
    val outline = Color(0xFF44445A)
    val outlineVariant = Color(0xFF2C2C3E)

    // Tile icon background colors (12% opacity)
    val tileBlue = Color(0xFF3B82F6)
    val tileAmber = Color(0xFFF59E0B)
    val tileGreen = Color(0xFF10B981)
    val tilePurple = Color(0xFF8B5CF6)
}

private val MangaLensDarkColorScheme = darkColorScheme(
    primary = MangaLensColors.primary,
    onPrimary = MangaLensColors.onPrimary,
    primaryContainer = MangaLensColors.primaryContainer,
    onPrimaryContainer = MangaLensColors.onPrimaryContainer,
    secondary = MangaLensColors.primaryVariant,
    onSecondary = MangaLensColors.onPrimary,
    tertiary = MangaLensColors.tilePurple,
    surface = MangaLensColors.surface,
    surfaceVariant = MangaLensColors.surfaceVariant,
    surfaceContainer = MangaLensColors.surfaceContainer,
    surfaceContainerHigh = MangaLensColors.surfaceContainer,
    surfaceContainerHighest = MangaLensColors.surfaceVariant,
    onSurface = MangaLensColors.onSurface,
    onSurfaceVariant = MangaLensColors.onSurfaceVariant,
    error = MangaLensColors.error,
    onError = MangaLensColors.onPrimary,
    outline = MangaLensColors.outline,
    outlineVariant = MangaLensColors.outlineVariant,
    background = MangaLensColors.surface,
    onBackground = MangaLensColors.onSurface,
)

private val MangaLensTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun MangaLensTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MangaLensDarkColorScheme,
        typography = MangaLensTypography,
        content = content,
    )
}
