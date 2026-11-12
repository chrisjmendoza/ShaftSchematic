package com.android.shaftschematic.util

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * User-friendly presets for preview component colors.
 *
 * Important: These resolve to theme colors (no hard-coded palette).
 *
 * Dark ground: a translucent fill that reads on a white ground all but disappears on a dark one,
 * so on a dark ground ([isDarkPreviewGround]) the neutral greys sit nearer the foreground and
 * every fill alpha roughly doubles ([fillAlpha]). A light ground resolves exactly as it always
 * has. The decision keys on the scheme's own `surface`, never on a theme flag, so the
 * high-contrast dark scheme takes it too.
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

    fun resolve(scheme: ColorScheme, customRole: PreviewColorRole): Color {
        val dark = scheme.isDarkPreviewGround()
        return when (this) {
            TRANSPARENT -> Color.Transparent
            // Neutral greys (theme-aware): interpolate between ground and foreground.
            STAINLESS -> lerp(scheme.surface, scheme.onSurface, if (dark) STAINLESS_LERP_DARK else STAINLESS_LERP_LIGHT)
            STEEL -> lerp(scheme.surface, scheme.onSurface, if (dark) STEEL_LERP_DARK else STEEL_LERP_LIGHT)
            // Warm tint: tertiary is the scheme's bronze.
            BRONZE -> scheme.tertiary
            CUSTOM -> customRole.resolve(scheme)
        }
    }

    /**
     * The alpha a component fill of this preset takes in the preview and the template
     * thumbnails — the ONE table both call sites read. [fallback] is the per-kind alpha a
     * [CUSTOM] fill takes on a light ground; on a dark ground it doubles, capped at
     * [CUSTOM_ALPHA_DARK_MAX].
     */
    fun fillAlpha(darkGround: Boolean, fallback: Float): Float = when (this) {
        TRANSPARENT -> 0f
        STEEL -> if (darkGround) 0.34f else 0.18f
        STAINLESS -> if (darkGround) 0.28f else 0.14f
        BRONZE -> if (darkGround) 0.38f else 0.16f
        CUSTOM -> if (darkGround) (fallback * 2f).coerceAtMost(CUSTOM_ALPHA_DARK_MAX) else fallback
    }

    companion object {
        const val STAINLESS_LERP_LIGHT = 0.50f
        const val STEEL_LERP_LIGHT = 0.75f
        const val STAINLESS_LERP_DARK = 0.68f
        const val STEEL_LERP_DARK = 0.90f
        const val CUSTOM_ALPHA_DARK_MAX = 0.45f
    }
}

/** Whether this scheme's preview ground is dark — the switch for the dark-ground preset rule. */
fun ColorScheme.isDarkPreviewGround(): Boolean = surface.luminance() < 0.5f
