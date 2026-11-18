package ir.irandetector.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.irandetector.app.settings.ThemeMode

// ---------------------------------------------------------------------------
// Brand palette. Emerald green accent on a deep, slightly green-shifted charcoal.
// Colours are defined once here and referenced everywhere else (no stray hex
// values in composables).
// ---------------------------------------------------------------------------
val Emerald = Color(0xFF2EE59D)
val EmeraldDeep = Color(0xFF12B981)
val EmeraldDark = Color(0xFF0B7A57)

private val Ink = Color(0xFF07120E)
private val InkElevated = Color(0xFF0D1A15)
private val InkCard = Color(0xFF12211B)
private val InkBorder = Color(0xFF1E332B)
private val TextPrimary = Color(0xFFEAF6F0)
private val TextSecondary = Color(0xFF9BB3A8)
private val Amber = Color(0xFFF5B83D)
private val Danger = Color(0xFFFF6B6B)
private val Sky = Color(0xFF5AC8FA)

private val DarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = Color(0xFF04140D),
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Color(0xFFDFFFF1),
    secondary = Sky,
    onSecondary = Color(0xFF00131C),
    tertiary = Amber,
    onTertiary = Color(0xFF1F1500),
    background = Ink,
    onBackground = TextPrimary,
    surface = InkElevated,
    onSurface = TextPrimary,
    surfaceVariant = InkCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = InkCard,
    surfaceContainerHigh = Color(0xFF16281F),
    error = Danger,
    onError = Color(0xFF2A0000),
    outline = InkBorder,
    outlineVariant = Color(0xFF182A23),
)

private val LightColors = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F7E2),
    onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF0F7EA8),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF6FBF8),
    onBackground = Color(0xFF0B1A14),
    surface = Color.White,
    onSurface = Color(0xFF0B1A14),
    surfaceVariant = Color(0xFFE6F1EC),
    onSurfaceVariant = Color(0xFF486057),
    error = Color(0xFFC0292B),
    outline = Color(0xFFCBDCD4),
)

/**
 * Extra semantic colours that Material 3 does not define but the app needs for
 * confidence tiers. Provided through a CompositionLocal so screens read
 * `AppPalette.current.verified` instead of guessing.
 */
data class SemanticColors(
    val verified: Color,
    val probable: Color,
    val uncertain: Color,
    val tierTrack: Color,
) {
    fun forTier(tier: ir.irandetector.app.domain.ConfidenceTier): Color = when (tier) {
        ir.irandetector.app.domain.ConfidenceTier.VERIFIED -> verified
        ir.irandetector.app.domain.ConfidenceTier.PROBABLE -> probable
        ir.irandetector.app.domain.ConfidenceTier.UNCERTAIN -> uncertain
        ir.irandetector.app.domain.ConfidenceTier.NONE -> uncertain
    }
}

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(verified = Emerald, probable = Sky, uncertain = Amber, tierTrack = InkBorder)
}

/** Typography: system font (no font files = no APK weight, full Persian shaping support). */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        letterSpacing = 0.3.sp,
    ),
)

object Dimens {
    val screenPadding = 20.dp
    val cardRadius = 20.dp
    val smallRadius = 12.dp
    val itemSpacing = 12.dp
    val sectionSpacing = 24.dp
}

@Composable
fun IranDetectorTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val semantic = if (dark) {
        SemanticColors(verified = Emerald, probable = Sky, uncertain = Amber, tierTrack = InkBorder)
    } else {
        SemanticColors(
            verified = EmeraldDark,
            probable = Color(0xFF0F7EA8),
            uncertain = Color(0xFFA06A00),
            tierTrack = Color(0xFFD7E5DE),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
