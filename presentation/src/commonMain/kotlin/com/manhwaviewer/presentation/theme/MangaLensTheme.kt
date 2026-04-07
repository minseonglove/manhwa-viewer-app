package com.manhwaviewer.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ───────────────────────────────────────────��────────────────
// Apple Design System — Color Tokens
// Reference: https://github.com/VoltAgent/awesome-design-md
// ────────────────────────────────────────────────────────────

object MangaLensColors {

    // ── Interactive (Apple Blue family) ──
    /** #0071E3 — Primary CTA, button fills. The ONLY chromatic accent. */
    val blue = Color(0xFF0071E3)

    /** #2997FF — Links & highlights on dark backgrounds. Higher luminance for contrast. */
    val blueHi = Color(0xFF2997FF)

    /** rgba(41,151,255,0.16) — Blue tinted background for status chips, selections. */
    val blueBg = Color(0x292997FF)

    /** rgba(0,113,227,0.18) — Active/selected button background on dark surfaces. */
    val blueActiveBg = Color(0x2E0071E3)

    // ── Semantic: Success ──
    /** #30D158 — iOS system green. Status success, completion. */
    val green = Color(0xFF30D158)

    /** rgba(48,209,88,0.16) — Green tinted background for success chips. */
    val greenBg = Color(0x2930D158)

    /** #10B981 — Pipeline/bounding box green. Emerald accent for visual indicators. */
    val emerald = Color(0xFF10B981)

    // ── Semantic: Warning ──
    /** #F59E0B — Amber/orange. Pipeline current stage, warnings. */
    val amber = Color(0xFFF59E0B)

    // ── Semantic: Error ──
    /** #FF453A — iOS system red. Error states, failures. */
    val red = Color(0xFFFF453A)

    /** rgba(255,69,58,0.14) — Red tinted background for error chips. */
    val redBg = Color(0x24FF453A)

    // ── Surfaces (Dark mode — Apple dark surface scale) ──
    /** #000000 — Pure black. Immersive backgrounds, viewer. */
    val black = Color(0xFF000000)

    /** #1C1C1E — iOS system grouped background. Cards, panels. */
    val surface1 = Color(0xFF1C1C1E)

    /** rgba(28,28,30,0.88) — Translucent panel/HUD background (glass morphism). */
    val surfaceGlass = Color(0xE01C1C1E)

    /** #111118 — Deep dark surface. App background. */
    val surfaceDeep = Color(0xFF111118)

    /** #1C1C26 — Slightly lighter dark surface variant. */
    val surfaceVariant = Color(0xFF1C1C26)

    /** #22222E — Container surface, elevated areas. */
    val surfaceContainer = Color(0xFF22222E)

    // ── Text on dark backgrounds (iOS label hierarchy) ──
    /** #FFFFFF — Primary text (label). */
    val textPrimary = Color(0xFFFFFFFF)

    /** rgba(235,235,245,0.60) — Secondary text (secondaryLabel). */
    val textSecondary = Color(0x99EBEBF5)

    /** rgba(235,235,245,0.30) — Tertiary text (tertiaryLabel). */
    val textTertiary = Color(0x4DEBEBF5)

    /** rgba(235,235,245,0.18) — Quaternary/placeholder text. */
    val textQuaternary = Color(0x2EEBEBF5)

    // ── Separators & Borders ──
    /** rgba(255,255,255,0.13) — Standard separator/border on dark surfaces. */
    val separator = Color(0x21FFFFFF)

    /** rgba(255,255,255,0.09) — Subtle divider. */
    val separatorLight = Color(0x17FFFFFF)

    /** rgba(255,255,255,0.15) — Inactive control border. */
    val borderInactive = Color(0x26FFFFFF)

    // ── Fill layers (iOS system fills on dark) ──
    /** rgba(120,120,128,0.18) — Segmented control, switch background. */
    val fillSecondary = Color(0x2E787880)

    /** rgba(255,255,255,0.08) — Subtle fill (inactive button background). */
    val fillTertiary = Color(0x14FFFFFF)

    /** rgba(255,255,255,0.06) — Extra subtle fill (info box background). */
    val fillQuaternary = Color(0x0FFFFFFF)

    /** rgba(255,255,255,0.14) — Slider track, progress background. */
    val fillTrack = Color(0x24FFFFFF)

    /** rgba(255,255,255,0.20) — Pending/waiting pipeline connector. */
    val fillPending = Color(0x33FFFFFF)

    // ── Tile icon colors (Home screen) ──
    val tileBlue = Color(0xFF3B82F6)
    val tileAmber = Color(0xFFF59E0B)
    val tileGreen = Color(0xFF10B981)
    val tilePurple = Color(0xFF8B5CF6)

    // ── Gradients ──
    /** Toolbar gradient: rgba(0,0,0,0.82) → transparent */
    val toolbarGradient = Brush.verticalGradient(
        listOf(Color(0xD1000000), Color.Transparent),
    )

    // ── Legacy aliases (Material3 color scheme mapping) ──
    val primary get() = blue
    val primaryVariant get() = Color(0xFF2F6EAF)
    val onPrimary get() = textPrimary
    val primaryContainer get() = Color(0xFF1C3A5C)
    val onPrimaryContainer get() = Color(0xFFD0E8FF)
    val surface get() = surfaceDeep
    val onSurface get() = Color(0xFFE8E8F0)
    val onSurfaceVariant get() = Color(0xFFA0A0B8)
    val error get() = Color(0xFFCF6679)
    val outline get() = Color(0xFF44445A)
    val outlineVariant get() = Color(0xFF2C2C3E)
}

// ────────────────────────────────────────────────────────────
// Material3 Color Scheme
// ────────────────────────────────────────────────────────────

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

// ────────────────────────────────────────────────────────────
// Typography — Apple SF Pro inspired scale
// SF Pro Display at 20sp+, SF Pro Text below
// Negative letter-spacing at all sizes (Apple characteristic)
// ────────────────────────────────────────────────────────────

private val MangaLensTypography = Typography(
    // Display — SF Pro Display range (20sp+)
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.28).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.24).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.20).sp,
    ),

    // Body — SF Pro Text range (<20sp)
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.374).sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.224).sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.12).sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.12).sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.12).sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = (-0.08).sp,
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
