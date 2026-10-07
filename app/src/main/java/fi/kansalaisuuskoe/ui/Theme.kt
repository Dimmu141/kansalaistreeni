package fi.kansalaisuuskoe.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import fi.kansalaisuuskoe.data.ThemePreference

private val LightColors = lightColorScheme(
    primary = Color(0xFF164E63), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFF5), onPrimaryContainer = Color(0xFF123D4E),
    secondary = Color(0xFF47635A), secondaryContainer = Color(0xFFDDECE4),
    background = Color(0xFFF8FAFC), surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF182B34), onSurfaceVariant = Color(0xFF465B65),
    surfaceContainer = Color(0xFFECF2F5), surfaceContainerLow = Color(0xFFF1F5F8),
    surfaceContainerHigh = Color(0xFFE5EDF1), outlineVariant = Color(0xFFC4D0D7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4D7E9), onPrimary = Color(0xFF073544),
    primaryContainer = Color(0xFF164E63), onPrimaryContainer = Color(0xFFDCEFF5),
    secondary = Color(0xFFB5D1C2), secondaryContainer = Color(0xFF284A3D),
    background = Color(0xFF101C22), surface = Color(0xFF101C22),
    onSurface = Color(0xFFE2EDF2), onSurfaceVariant = Color(0xFFBACBD3),
    surfaceContainer = Color(0xFF1B2B33), surfaceContainerLow = Color(0xFF16252D),
    surfaceContainerHigh = Color(0xFF24353E), outlineVariant = Color(0xFF40535D),
)
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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
