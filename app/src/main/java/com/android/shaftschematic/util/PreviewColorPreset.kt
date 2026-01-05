package com.android.shaftschematic.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * User-friendly presets for preview component colors.
 *
 * Important: These resolve to theme colors (no hard-coded palette).
 */
enum class PreviewColorPreset {
    TRANSPARENT,
    STAINLESS,
    STEEL,
    BRONZE,
    CUSTOM;

    fun uiLabel(): String = when (this) {
        TRANSPARENT -> "Transparent"
        STAINLESS -> "Stainless"
        STEEL -> "Steel"
        BRONZE -> "Bronze"
        CUSTOM -> "Custom"
    }

    fun resolve(scheme: ColorScheme, customRole: PreviewColorRole): Color = when (this) {
        TRANSPARENT -> Color.Transparent
        // Light silver-ish: typically a light neutral in the active theme.
        STAINLESS -> scheme.surfaceVariant
        // Darker neutral: outline tends to be darker/stronger than surfaceVariant.
        STEEL -> scheme.outline
        // Warm tint: tertiary is usually the theme’s accent alternative.
        BRONZE -> scheme.tertiary
        CUSTOM -> customRole.resolve(scheme)
    }
}
