package com.android.shaftschematic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.android.shaftschematic.settings.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// High-contrast schemes: pure black/white grounds, near-zero mid-grey usage, and bold
// container colors so selected/unselected states separate without relying on tint subtlety.
// Accessibility posture: aim for WCAG AAA-level figure/ground on every text role.
private val HighContrastLightColorScheme = lightColorScheme(
    primary = HcBlue,
    onPrimary = Color.White,
    primaryContainer = HcBlue,
    onPrimaryContainer = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.Black,
    onSecondaryContainer = Color.White,
    tertiary = HcBronzeDark,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,
    error = HcErrorLight,
    onError = Color.White,
    errorContainer = HcErrorLight,
    onErrorContainer = Color.White,
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = HcAmber,
    onPrimary = Color.Black,
    primaryContainer = HcAmber,
    onPrimaryContainer = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color.White,
    onSecondaryContainer = Color.Black,
    tertiary = HcBronzeBright,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color.White,
    outline = Color.White,
    outlineVariant = Color.White,
    error = HcErrorDark,
    onError = Color.Black,
    errorContainer = HcErrorDark,
    onErrorContainer = Color.Black,
)

/**
 * App theme, driven by Settings → Appearance.
 *
 * Contract
 * - [AppThemeMode.LIGHT] + `highContrast = false` must reproduce the app's historical
 *   presentation (the pre-Appearance bare `MaterialTheme` look) — it is the default, so an
 *   update changes nothing until the user opts in.
 * - Paper-sheet canvases (undercut / wear / runout drawing surfaces) and PDF output are
 *   theme-independent: white sheet, fixed black ink. The theme must never be consulted for
 *   sheet ink — see `docs/Appearance.md`.
 * - No dynamic (Material You) color: schemes are fixed so the preview-color presets
 *   (Stainless/Steel/Bronze) resolve predictably. Revisit deliberately if wallpaper-driven
 *   color is ever wanted.
 */
@Composable
fun ShaftSchematicTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColorScheme
        highContrast -> HighContrastLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
