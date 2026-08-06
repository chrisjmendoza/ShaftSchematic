package com.android.shaftschematic.util

import androidx.compose.ui.graphics.Color
import com.android.shaftschematic.geom.UNDERCUT_SECTION_FILL_ALPHA

/**
 * User-selectable styling for the on-screen Undercut Drawing (overview canvas and the zoomed
 * window overlay). The undercut sheet is a paper-white canvas in every app theme, so these
 * resolve to **fixed ink colors** — never theme roles, which wash out on white in dark theme.
 *
 * PDF output is deliberately NOT driven by this style: the printed drawing keeps the standard
 * black-ink shading (`UndercutPdfComposer`), same posture as the preview-colors system never
 * leaking into `ShaftPdfComposer`.
 */
enum class UndercutShadeColor {
    GREY,
    BRONZE,
    BLUE;

    fun uiLabel(): String = when (this) {
        GREY -> "Grey"
        BRONZE -> "Bronze"
        BLUE -> "Blue"
    }

    /** Opaque base ink the shade alphas are applied to. */
    val base: Color
        get() = when (this) {
            GREY -> Color.Black
            BRONZE -> Color(0xFF8B5A00)
            BLUE -> Color(0xFF1E5AA8)
        }

    companion object {
        fun fromName(raw: String?): UndercutShadeColor =
            if (raw.isNullOrBlank()) GREY else runCatching { valueOf(raw) }.getOrDefault(GREY)
    }
}

enum class UndercutShadeIntensity {
    LIGHT,
    STANDARD,
    DARK;

    fun uiLabel(): String = when (this) {
        LIGHT -> "Light"
        STANDARD -> "Standard"
        DARK -> "Dark"
    }

    /** Multiplier on the standard shade weight. */
    val multiplier: Float
        get() = when (this) {
            LIGHT -> 0.5f
            STANDARD -> 1.0f
            DARK -> 1.75f
        }

    companion object {
        fun fromName(raw: String?): UndercutShadeIntensity =
            if (raw.isNullOrBlank()) STANDARD else runCatching { valueOf(raw) }.getOrDefault(STANDARD)
    }
}

/**
 * Resolved styling for the undercut drawing surfaces.
 *
 * The alpha ladder hangs off the shared section-fill constant so the "section core fills one
 * step lighter than the liner" invariant holds at every intensity:
 * liner = 2 × [UNDERCUT_SECTION_FILL_ALPHA] × multiplier, section and body = half the liner.
 * At STANDARD that reproduces the fixed shades the screens used before this setting existed.
 *
 * [lineArt] is the color-removal mode: every fill goes fully transparent — the drawing is
 * white with black outlines only. Outline ink is not part of this style; the sheet screens pin
 * it to black themselves.
 */
data class UndercutStyle(
    val lineArt: Boolean = false,
    val shadeColor: UndercutShadeColor = UndercutShadeColor.GREY,
    val intensity: UndercutShadeIntensity = UndercutShadeIntensity.STANDARD,
) {
    private val linerAlpha: Float
        get() = (2f * UNDERCUT_SECTION_FILL_ALPHA * intensity.multiplier).coerceAtMost(1f)

    /** Liner span fill on the sheet (the darkest shade). */
    fun linerFill(): Color =
        if (lineArt) Color.Transparent else shadeColor.base.copy(alpha = linerAlpha)

    /** Body/taper fill on the overview canvas — one step lighter than the liner. */
    fun bodyFill(): Color =
        if (lineArt) Color.Transparent else shadeColor.base.copy(alpha = linerAlpha / 2f)

    /** Undercut section core fill — one step lighter than the liner, same weight as bodies. */
    fun sectionFill(): Color =
        if (lineArt) Color.Transparent else shadeColor.base.copy(alpha = linerAlpha / 2f)
}
