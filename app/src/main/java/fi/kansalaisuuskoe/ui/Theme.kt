package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import fi.kansalaisuuskoe.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import fi.kansalaisuuskoe.data.ThemePreference

/** Kansalaistreeni design system tokens (see the Kansalaistreeni Design System artifact). */
@Immutable
data class KsColors(
    val surface0: Color, val surface1: Color, val surface2: Color,
    val line: Color, val lineStrong: Color,
    val ink: Color, val inkMuted: Color,
    val brand: Color, val brandPressed: Color, val onBrand: Color, val brandSoft: Color, val onBrandSoft: Color,
    val correct: Color, val correctSoft: Color, val onCorrect: Color, val onCorrectSoft: Color,
    val miss: Color, val missSoft: Color, val onMiss: Color, val onMissSoft: Color,
    val birch: Color, val birchSoft: Color, val onBirchSoft: Color,
    val focus: Color, val scrim: Color,
)

private val LightKs = KsColors(
    surface0 = Color(0xFFF5F7F6), surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFE9EFF1),
    line = Color(0xFFCDD8DC), lineStrong = Color(0xFF7D929B),
    ink = Color(0xFF13262E), inkMuted = Color(0xFF4C606A),
    brand = Color(0xFF164E63), brandPressed = Color(0xFF0E3A4B), onBrand = Color(0xFFFFFFFF), brandSoft = Color(0xFFDCEDF3), onBrandSoft = Color(0xFF0F3B4D),
    correct = Color(0xFF1C7650), correctSoft = Color(0xFFDCF2E7), onCorrect = Color(0xFFFFFFFF), onCorrectSoft = Color(0xFF0D4A31),
    miss = Color(0xFFB0441C), missSoft = Color(0xFFFBE7DD), onMiss = Color(0xFFFFFFFF), onMissSoft = Color(0xFF6E2A10),
    birch = Color(0xFFE3B341), birchSoft = Color(0xFFFBF0D2), onBirchSoft = Color(0xFF5C4510),
    focus = Color(0xFF1F6F99), scrim = Color(0x520E191E),
)
private val DarkKs = KsColors(
    surface0 = Color(0xFF0E191E), surface1 = Color(0xFF16242B), surface2 = Color(0xFF203139),
    line = Color(0xFF364952), lineStrong = Color(0xFF6D838C),
    ink = Color(0xFFE6EFF2), inkMuted = Color(0xFFA6B9C1),
    brand = Color(0xFF8FD0E6), brandPressed = Color(0xFFB8E4F2), onBrand = Color(0xFF06293A), brandSoft = Color(0xFF173F4F), onBrandSoft = Color(0xFFD6EEF6),
    correct = Color(0xFF6CD3A3), correctSoft = Color(0xFF123829), onCorrect = Color(0xFF05261A), onCorrectSoft = Color(0xFFC9F1DD),
    miss = Color(0xFFFF9D78), missSoft = Color(0xFF43200F), onMiss = Color(0xFF2E1205), onMissSoft = Color(0xFFFFDCCD),
    birch = Color(0xFFE8C167), birchSoft = Color(0xFF3A2F12), onBirchSoft = Color(0xFFF6E2AE),
    focus = Color(0xFF9FD8F0), scrim = Color(0x80000000),
)

private fun KsColors.scheme(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = brand, onPrimary = onBrand, primaryContainer = brandSoft, onPrimaryContainer = onBrandSoft,
    secondary = brand, onSecondary = onBrand, secondaryContainer = brandSoft, onSecondaryContainer = onBrandSoft,
    tertiary = correct, onTertiary = onCorrect, tertiaryContainer = correctSoft, onTertiaryContainer = onCorrectSoft,
    error = miss, onError = onMiss, errorContainer = missSoft, onErrorContainer = onMissSoft,
    background = surface0, onBackground = ink, surface = surface0, onSurface = ink,
    surfaceVariant = surface2, onSurfaceVariant = inkMuted, surfaceTint = brand,
    surfaceContainerLowest = surface1, surfaceContainerLow = surface1, surfaceContainer = surface1,
    surfaceContainerHigh = surface1, surfaceContainerHighest = surface2,
    outline = lineStrong, outlineVariant = line, scrim = scrim,
)

/** Figtree (OFL, `licenses/Figtree-OFL.txt`), bundled as one variable font so the offline app never falls back to Roboto. */
@OptIn(ExperimentalTextApi::class)
internal val KsFont: FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(R.font.figtree, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = KsFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight(weight), letterSpacing = tracking.em,
)

/** Named styles from tokens.json that have no exact Material slot. */
object KsType {
    val display = style(56, 60, 700, -0.02)
    val headline = style(28, 34, 700, -0.01)
    val question = style(24, 32, 600)
    val verdict = style(22, 28, 700)
    val option = style(17, 24, 500)
    val title = style(18, 24, 600)
    val bodyLarge = style(17, 26, 400)
    val body = style(15, 22, 400)
    val label = style(15, 20, 600)
    val caption = style(13, 18, 500)
    val overline = style(12, 16, 700, 0.06)
}

private val KsTypography = Typography(
    displayLarge = KsType.display, displayMedium = KsType.display, displaySmall = style(44, 52, 700, -0.02),
    headlineLarge = style(32, 40, 700, -0.01), headlineMedium = KsType.headline, headlineSmall = KsType.question,
    titleLarge = KsType.verdict, titleMedium = KsType.title, titleSmall = style(16, 22, 600),
    bodyLarge = KsType.bodyLarge, bodyMedium = KsType.body, bodySmall = style(13, 18, 400),
    labelLarge = KsType.label, labelMedium = KsType.caption, labelSmall = KsType.overline,
)

private val KsShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** Motion durations in ms (tokens.json `duration`). */
object KsMotion {
    const val PRESS = 90
    const val SELECT = 160
    const val REVEAL = 260
    const val SHEET = 320
    const val PROGRESS = 480
}

internal val LocalKsColors = staticCompositionLocalOf { LightKs }

/** Design-system colours, including the verdict colours that Material has no role for. */
object Ks {
    val colors: KsColors @Composable get() = LocalKsColors.current
}

@Composable
fun CitizenshipTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) { ThemePreference.SYSTEM -> isSystemInDarkTheme(); ThemePreference.DARK -> true; ThemePreference.LIGHT -> false }
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colors = if (dark) DarkKs else LightKs
    CompositionLocalProvider(LocalKsColors provides colors) {
        MaterialTheme(colorScheme = colors.scheme(dark), typography = KsTypography, shapes = KsShapes, content = content)
    }
}
