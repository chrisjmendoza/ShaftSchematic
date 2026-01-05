package com.android.shaftschematic.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Theme-based color choices for the Preview renderer.
 *
 * Persisted in Settings as enum names; resolved to actual colors via [ColorScheme].
 */
enum class PreviewColorRole {
    TRANSPARENT,
    MONOCHROME,
    PRIMARY,
    SECONDARY,
    TERTIARY,
    SURFACE_VARIANT,
    OUTLINE,
    ON_SURFACE,
    ERROR;

    fun uiLabel(): String = when (this) {
        TRANSPARENT -> "Transparent"
        MONOCHROME -> "Monochrome"
        PRIMARY -> "Primary"
        SECONDARY -> "Secondary"
        TERTIARY -> "Tertiary"
        SURFACE_VARIANT -> "Surface Variant"
        OUTLINE -> "Outline"
        ON_SURFACE -> "On Surface"
        ERROR -> "Error"
    }

    fun resolve(scheme: ColorScheme): Color = when (this) {
        TRANSPARENT -> Color.Transparent
        MONOCHROME -> Color.Black
        PRIMARY -> scheme.primary
        SECONDARY -> scheme.secondary
        TERTIARY -> scheme.tertiary
        SURFACE_VARIANT -> scheme.surfaceVariant
        OUTLINE -> scheme.outline
        ON_SURFACE -> scheme.onSurface
        ERROR -> scheme.error
    }
}
