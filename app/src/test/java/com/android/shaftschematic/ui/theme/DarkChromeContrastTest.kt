package com.android.shaftschematic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import com.android.shaftschematic.ui.input.DARK_DISABLED_LABEL_ALPHA
import com.android.shaftschematic.ui.input.DARK_DISABLED_VALUE_ALPHA
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.isDarkPreviewGround
import com.android.shaftschematic.util.previewShaftGrey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The dark scheme's chrome, measured: every text role on every surface it lands on, the
 * non-text boundaries, the steel-blue accent against the error and caution rungs, the severity
 * ladder under colour-vision deficiency, and the shaft preview's shaded / unshaded states. The
 * light scheme's resolved roles are pinned so the default Light theme draws what it always did.
 *
 * Targets: field values and labels 7:1 (read at arm's length), other text 4.5:1, essential
 * non-text (outlines, the accent as a control colour) 3:1.
 */
class DarkChromeContrastTest {

    private val dark = DarkColorScheme

    private fun lin(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertContrast(what: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        println("$what: ${"%.2f".format(c)}:1")
        assertTrue("$what is ${"%.2f".format(c)}:1, needs $min:1", c >= min)
    }

    /** Machado et al. (2009) severity-1 protanopia / deuteranopia, applied in linear RGB. */
    private fun simulate(c: Color, m: Array<DoubleArray>): Color {
        val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
        fun enc(v: Double): Float {
            val x = v.coerceIn(0.0, 1.0)
            return (if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055).toFloat()
        }
        return Color(
            enc(m[0][0] * r + m[0][1] * g + m[0][2] * b),
            enc(m[1][0] * r + m[1][1] * g + m[1][2] * b),
            enc(m[2][0] * r + m[2][1] * g + m[2][2] * b),
        )
    }

    private val protan = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    )
    private val deutan = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.011820, 0.042940, 0.968881),
    )

    /** Oklab hue angle in degrees. */
    private fun hue(c: Color): Double {
        val o = c.convert(ColorSpaces.Oklab)
        return Math.toDegrees(atan2(o.blue.toDouble(), o.green.toDouble()))
    }

    private fun chroma(c: Color): Double {
        val o = c.convert(ColorSpaces.Oklab)
        return sqrt((o.green * o.green + o.blue * o.blue).toDouble())
    }

    private fun hueGap(a: Color, b: Color): Double {
        val d = abs(hue(a) - hue(b)) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Where text lands in this app: the ground, the sidebar, sheets, dialogs, and the cards. */
    private val surfaces: List<Pair<String, Color>>
        get() = listOf(
            "background" to dark.background,
            "surfaceContainerLow (sidebar)" to dark.surfaceContainerLow,
            "surfaceContainer" to dark.surfaceContainer,
            "surfaceContainerHigh (dialog)" to dark.surfaceContainerHigh,
            "surfaceContainerHighest" to dark.surfaceContainerHighest,
            "surfaceVariant (carousel card)" to dark.surfaceVariant,
        )

    @Test
    fun `text roles read on every dark surface`() {
        surfaces.forEach { (name, bg) ->
            assertContrast("onSurface on $name", dark.onSurface, bg, 7.0)
            assertContrast("onSurfaceVariant on $name", dark.onSurfaceVariant, bg, 7.0)
        }
    }

    @Test
    fun `boundaries a control is found by reach 3 to 1`() {
        assertContrast("outline on background", dark.outline, dark.background, 3.0)
        assertContrast("outline on the card (field border)", dark.outline, dark.surfaceVariant, 3.0)
        // Dividers and default chip borders: visible, not ruling the page.
        assertContrast("outlineVariant on background", dark.outlineVariant, dark.background, 2.2)
    }

    @Test
    fun `the steel-blue accent reads as text, as a control and under its own label`() {
        assertContrast("primary on background", dark.primary, dark.background, 7.0)
        assertContrast("primary on a dialog", dark.primary, dark.surfaceContainerHigh, 4.5)
        assertContrast("primary on the card", dark.primary, dark.surfaceVariant, 4.5)
        assertContrast("onPrimary on primary (filled button)", dark.onPrimary, dark.primary, 4.5)
        assertContrast("onPrimaryContainer", dark.onPrimaryContainer, dark.primaryContainer, 7.0)
        assertContrast("onSecondaryContainer (selected chip, sidebar item)", dark.onSecondaryContainer, dark.secondaryContainer, 7.0)
        assertContrast("inversePrimary on the snackbar", dark.inversePrimary, dark.inverseSurface, 4.5)
        // No purple left in the dark accent: steel blue, low chroma.
        assertTrue("primary is not blue", hue(dark.primary) in -120.0..-90.0)
        assertTrue("primary is too saturated for a full-width button", chroma(dark.primary) < 0.09)
    }

    @Test
    fun `the accent, error and caution are three different hues`() {
        val accent = dark.primary
        assertTrue("accent vs error hue", hueGap(accent, dark.error) > 90.0)
        assertTrue("accent vs error container hue", hueGap(accent, dark.errorContainer) > 90.0)
        assertTrue("accent vs caution hue", hueGap(accent, dark.tertiaryContainer) > 90.0)
        assertTrue("accent vs bronze liner hue", hueGap(accent, dark.tertiary) > 90.0)
    }

    @Test
    fun `the dark severity ladder steps in lightness, so it survives protanopia and deuteranopia`() {
        val card = dark.surfaceVariant
        // Error above the card, caution below it.
        assertTrue("error container is not lighter than the card", luminance(dark.errorContainer) > luminance(card))
        assertTrue("caution container is not darker than the card", luminance(dark.tertiaryContainer) < luminance(card))
        assertContrast("onErrorContainer", dark.onErrorContainer, dark.errorContainer, 7.0)
        assertContrast("onTertiaryContainer (caution)", dark.onTertiaryContainer, dark.tertiaryContainer, 7.0)
        listOf("protan" to protan, "deutan" to deutan).forEach { (name, m) ->
            val e = simulate(dark.errorContainer, m)
            val w = simulate(dark.tertiaryContainer, m)
            assertContrast("$name: error vs caution container", e, w, 1.4)
        }
    }

    @Test
    fun `chrome inks a dark ground swaps in`() {
        // The cards' selected chip is the foreground with ground-coloured text.
        assertContrast("strong chip label", dark.surface, dark.onSurface, 7.0)
        assertContrast("strong chip on the card", dark.onSurface, dark.surfaceVariant, 3.0)
        // A disabled (derived) field on the card.
        val card = dark.surfaceVariant
        assertContrast("disabled value", dark.onSurface.copy(alpha = DARK_DISABLED_VALUE_ALPHA).compositeOver(card), card, 4.5)
        assertContrast("disabled label", dark.onSurfaceVariant.copy(alpha = DARK_DISABLED_LABEL_ALPHA).compositeOver(card), card, 4.5)
        // The accept ✓ on the ground and on the card.
        assertContrast("accept green on background", AcceptGreenDark, dark.background, 4.5)
        assertContrast("accept green on the card", AcceptGreenDark, card, 4.5)
        assertEquals(Color(0xFF2E7D32), AcceptGreenLight)
    }

    @Test
    fun `the shaft preview - unshaded grey, shaded taper and bronze liner stay apart`() {
        listOf(DarkColorScheme, HighContrastDarkColorScheme).forEach { s ->
            val grey = s.previewShaftGrey()!!
            val outline = PreviewColorPreset.STEEL.resolve(s, PreviewColorRole.PRIMARY)
            val taper = PreviewColorPreset.STEEL.resolve(s, PreviewColorRole.PRIMARY)
                .copy(alpha = PreviewColorPreset.STEEL.fillAlpha(true, 0.14f)).compositeOver(grey)
            val liner = PreviewColorPreset.BRONZE.resolve(s, PreviewColorRole.PRIMARY)
                .copy(alpha = PreviewColorPreset.BRONZE.fillAlpha(true, 0.16f)).compositeOver(grey)
            assertContrast("outline on background", outline, s.background, 7.0)
            assertContrast("outline on the bare-shaft grey", outline, grey, 3.0)
            assertContrast("bare-shaft grey off the ground", grey, s.background, 2.0)
            assertContrast("shaded taper vs unshaded grey", taper, grey, 1.5)
            assertContrast("liner vs unshaded grey", liner, grey, 1.5)
            // A liner is bronze by chroma, a taper is neutral — and under protanopia and
            // deuteranopia the bronze keeps its (yellow) chroma.
            assertTrue("liner fill is not warm enough", chroma(liner) > 0.05)
            assertTrue("taper fill is not neutral", chroma(taper) < 0.01)
            listOf(protan, deutan).forEach { m ->
                assertTrue("liner loses its chroma under CVD", chroma(simulate(liner, m)) > 0.05)
            }
            // The keyway void is the ground: a dark cut on the shaded taper.
            assertContrast("keyway void on the shaded taper", s.background, taper, 3.0)
            // The selection ring (the accent) reads against the ground.
            assertContrast("selection ring on the ground", s.primary, s.background, 3.0)
        }
    }

    /**
     * The default Light theme: every role is M3's baseline except the five the app assigns (and
     * the surface tint, which follows the assigned primary).
     */
    @Test
    fun `the light scheme resolves exactly as before`() {
        val base = lightColorScheme()
        val roles: List<Pair<String, (ColorScheme) -> Color>> = listOf(
            "onPrimary" to { it.onPrimary }, "primaryContainer" to { it.primaryContainer },
            "onPrimaryContainer" to { it.onPrimaryContainer }, "inversePrimary" to { it.inversePrimary },
            "onSecondary" to { it.onSecondary }, "secondaryContainer" to { it.secondaryContainer },
            "onSecondaryContainer" to { it.onSecondaryContainer }, "onTertiary" to { it.onTertiary },
            "background" to { it.background }, "onBackground" to { it.onBackground },
            "surface" to { it.surface }, "onSurface" to { it.onSurface },
            "surfaceVariant" to { it.surfaceVariant }, "onSurfaceVariant" to { it.onSurfaceVariant },
            "inverseSurface" to { it.inverseSurface },
            "inverseOnSurface" to { it.inverseOnSurface }, "error" to { it.error },
            "onError" to { it.onError }, "errorContainer" to { it.errorContainer },
            "onErrorContainer" to { it.onErrorContainer }, "outline" to { it.outline },
            "outlineVariant" to { it.outlineVariant }, "scrim" to { it.scrim },
            "surfaceBright" to { it.surfaceBright }, "surfaceDim" to { it.surfaceDim },
            "surfaceContainer" to { it.surfaceContainer }, "surfaceContainerHigh" to { it.surfaceContainerHigh },
            "surfaceContainerHighest" to { it.surfaceContainerHighest },
            "surfaceContainerLow" to { it.surfaceContainerLow },
            "surfaceContainerLowest" to { it.surfaceContainerLowest },
        )
        roles.forEach { (name, role) -> assertEquals("light $name moved", role(base), role(LightColorScheme)) }
        assertEquals(Color(0xFF6650A4), LightColorScheme.primary)
        // The tint follows the assigned primary, as it always has.
        assertEquals(LightColorScheme.primary, LightColorScheme.surfaceTint)
        assertEquals(Color(0xFF625B71), LightColorScheme.secondary)
        assertEquals(Color(0xFF7D5260), LightColorScheme.tertiary)
        assertEquals(Color(0xFFFFE2A9), LightColorScheme.tertiaryContainer)
        assertEquals(Color(0xFF4A3200), LightColorScheme.onTertiaryContainer)
        // Every dark-ground switch is off on a light ground.
        assertTrue(!LightColorScheme.isDarkPreviewGround())
        assertTrue(!HighContrastLightColorScheme.isDarkPreviewGround())
    }
}
