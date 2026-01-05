package com.android.shaftschematic.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
        // Neutral greys (theme-aware): interpolate between background and foreground.
        // In light theme: surface≈white, onSurface≈black.
        // In dark theme: surface≈dark, onSurface≈light.
        STAINLESS -> lerp(scheme.surface, scheme.onSurface, 0.50f)
        STEEL -> lerp(scheme.surface, scheme.onSurface, 0.75f)
        // Warm tint: tertiary is usually the theme’s accent alternative.
        BRONZE -> scheme.tertiary
        CUSTOM -> customRole.resolve(scheme)
    }
}
