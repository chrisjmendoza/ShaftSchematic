package com.android.shaftschematic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.android.shaftschematic.util.PreviewColorPreset
import com.android.shaftschematic.util.PreviewColorRole
import com.android.shaftschematic.util.isDarkPreviewGround
import com.android.shaftschematic.util.previewShaftGrey
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The editor preview on a dark ground: the neutral dark-grey scheme plus the dark-ground preset
 * rule (`PreviewColorPreset.resolve` / `fillAlpha`, keyed on the scheme's own `surface`).
 *
 * On M3's baseline purple-black, a liner's 16% bronze fill and a taper's 18% steel fill sat a
 * hair off the ground (on-device report). These measure what the preview actually paints — the
 * fill composited over the ground — with the WCAG contrast formula, and pin the light ground's
 * resolution so the default Light theme draws exactly what it always did.
 */
class PreviewColorDarkGroundTest {

    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** What the preview paints for a [preset] fill: the resolved colour at its alpha, over [ground]. */
    private fun painted(preset: PreviewColorPreset, scheme: ColorScheme, ground: Color): Color {
        val dark = scheme.isDarkPreviewGround()
        val fill = preset.resolve(scheme, PreviewColorRole.PRIMARY)
        return fill.copy(alpha = preset.fillAlpha(dark, fallback = 0.16f)).compositeOver(ground)
    }

    private fun oklabDistance(a: Color, b: Color): Double {
        val oa = a.convert(ColorSpaces.Oklab)
        val ob = b.convert(ColorSpaces.Oklab)
        val dl = (oa.red - ob.red).toDouble()
        val da = (oa.green - ob.green).toDouble()
        val db = (oa.blue - ob.blue).toDouble()
        return sqrt(dl * dl + da * da + db * db)
    }

    private val filled = listOf(PreviewColorPreset.STEEL, PreviewColorPreset.STAINLESS, PreviewColorPreset.BRONZE)

    @Test
    fun `the ground decides the rule, never a theme flag`() {
        assertFalse(LightColorScheme.isDarkPreviewGround())
        assertFalse(HighContrastLightColorScheme.isDarkPreviewGround())
        assertTrue(DarkColorScheme.isDarkPreviewGround())
        assertTrue(HighContrastDarkColorScheme.isDarkPreviewGround())
    }

    @Test
    fun `the dark scheme is a neutral grey, not M3's purple-black`() {
        assertEquals(Color(0xFF1F2023), DarkColorScheme.background)
        assertEquals(Color(0xFF1F2023), DarkColorScheme.surface)
        assertEquals(Color(0xFFE8E8EA), DarkColorScheme.onSurface)
        assertEquals(Color(0xFFE2B36B), DarkColorScheme.tertiary)
        // Neutral: the ground's channels sit within a few steps of each other.
        val g = DarkColorScheme.background
        assertTrue("ground carries a tint", maxOf(g.red, g.green, g.blue) - minOf(g.red, g.green, g.blue) < 0.02f)
        // Tonal elevation must lighten, never tint purple.
        val t = DarkColorScheme.surfaceTint
        assertTrue("surface tint is not neutral", maxOf(t.red, t.green, t.blue) - minOf(t.red, t.green, t.blue) < 0.04f)
        // The caution tier is untouched.
        assertEquals(WarnAmberDark, DarkColorScheme.tertiaryContainer)
        assertEquals(OnWarnAmberDark, DarkColorScheme.onTertiaryContainer)
    }

    @Test
    fun `every dark fill stands off the ground and the outline reads`() {
        val ground = DarkColorScheme.background
        filled.forEach { preset ->
            val c = contrast(painted(preset, DarkColorScheme, ground), ground)
            println("dark $preset fill on ground: ${"%.2f".format(c)}:1")
            assertTrue("$preset fill is ${"%.2f".format(c)}:1 on the dark ground, needs 1.6:1", c >= 1.6)
        }
        // The default outline (and rails, hatches, seal dashes) is the Steel preset at full alpha.
        val outline = PreviewColorPreset.STEEL.resolve(DarkColorScheme, PreviewColorRole.PRIMARY)
        val oc = contrast(outline, ground)
        println("dark outline on ground: ${"%.2f".format(oc)}:1")
        assertTrue("outline is ${"%.2f".format(oc)}:1, needs 7:1", oc >= 7.0)
    }

    @Test
    fun `a liner and a taper are different colours on the dark ground`() {
        val ground = DarkColorScheme.background
        val bronze = painted(PreviewColorPreset.BRONZE, DarkColorScheme, ground)
        val steel = painted(PreviewColorPreset.STEEL, DarkColorScheme, ground)
        val d = oklabDistance(bronze, steel)
        println("dark bronze vs steel fill, Oklab distance: ${"%.3f".format(d)}")
        assertTrue("bronze and steel fills are ${"%.3f".format(d)} apart in Oklab", d >= 0.04)
        // The difference is HUE, not just lightness: bronze is warm (Oklab b > 0), steel is not.
        val bOk = bronze.convert(ColorSpaces.Oklab)
        val sOk = steel.convert(ColorSpaces.Oklab)
        assertTrue("bronze fill is not warm", bOk.blue > 0.03f)
        assertTrue("steel fill is not neutral", kotlin.math.abs(sOk.blue) < 0.01f && kotlin.math.abs(sOk.green) < 0.01f)
    }

    /** The template thumbnails draw on a `surfaceVariant` card. */
    @Test
    fun `default thumbnail fills read on the dark card`() {
        val card = DarkColorScheme.surfaceVariant
        listOf(PreviewColorPreset.STEEL, PreviewColorPreset.BRONZE).forEach { preset ->
            val c = contrast(painted(preset, DarkColorScheme, card), card)
            println("dark $preset fill on thumbnail card: ${"%.2f".format(c)}:1")
            assertTrue("$preset fill is ${"%.2f".format(c)}:1 on the thumbnail card", c >= 1.6)
        }
    }

    /** High-contrast dark has a black ground, so it takes the same rule; its default fills read. */
    @Test
    fun `high contrast dark takes the rule and its default fills read`() {
        val scheme = HighContrastDarkColorScheme
        val ground = scheme.background
        filled.forEach { preset ->
            val c = contrast(painted(preset, scheme, ground), ground)
            println("hc-dark $preset fill on ground: ${"%.2f".format(c)}:1")
        }
        listOf(PreviewColorPreset.STEEL, PreviewColorPreset.BRONZE).forEach { preset ->
            val c = contrast(painted(preset, scheme, ground), ground)
            assertTrue("hc-dark $preset fill is ${"%.2f".format(c)}:1", c >= 1.6)
        }
        val outline = PreviewColorPreset.STEEL.resolve(scheme, PreviewColorRole.PRIMARY)
        val oc = contrast(outline, ground)
        println("hc-dark outline on ground: ${"%.2f".format(oc)}:1")
        assertTrue(oc >= 7.0)
    }

    /** The light ground resolves exactly as before the dark-ground rule existed. */
    @Test
    fun `a light ground is unchanged`() {
        listOf(LightColorScheme, HighContrastLightColorScheme).forEach { s ->
            assertEquals(lerp(s.surface, s.onSurface, 0.50f), PreviewColorPreset.STAINLESS.resolve(s, PreviewColorRole.PRIMARY))
            assertEquals(lerp(s.surface, s.onSurface, 0.75f), PreviewColorPreset.STEEL.resolve(s, PreviewColorRole.PRIMARY))
            assertEquals(s.tertiary, PreviewColorPreset.BRONZE.resolve(s, PreviewColorRole.PRIMARY))
            assertEquals(s.secondary, PreviewColorPreset.CUSTOM.resolve(s, PreviewColorRole.SECONDARY))
        }
        assertEquals(0f, PreviewColorPreset.TRANSPARENT.fillAlpha(false, 0.10f))
        assertEquals(0.18f, PreviewColorPreset.STEEL.fillAlpha(false, 0.10f))
        assertEquals(0.14f, PreviewColorPreset.STAINLESS.fillAlpha(false, 0.10f))
        assertEquals(0.16f, PreviewColorPreset.BRONZE.fillAlpha(false, 0.10f))
        listOf(0.10f, 0.14f, 0.16f).forEach { f ->
            assertEquals(f, PreviewColorPreset.CUSTOM.fillAlpha(false, f))
        }
        // The light scheme itself is the historical one.
        assertEquals(Color(0xFF6650A4), LightColorScheme.primary)
        assertEquals(Color(0xFF7D5260), LightColorScheme.tertiary)
        assertEquals(lightColorScheme().surface, LightColorScheme.surface)
        assertEquals(lightColorScheme().onSurface, LightColorScheme.onSurface)
    }

    @Test
    fun `the shaft is a grey that stands off a dark ground, and a light ground has none`() {
        assertNull(LightColorScheme.previewShaftGrey())
        assertNull(HighContrastLightColorScheme.previewShaftGrey())
        listOf(DarkColorScheme, HighContrastDarkColorScheme).forEach { scheme ->
            val grey = scheme.previewShaftGrey()!!
            assertEquals("the shaft grey is opaque", 1f, grey.alpha, 0f)
            val off = contrast(grey, scheme.background)
            assertTrue("shaft grey is ${"%.2f".format(off)}:1 off the ground, needs 2:1", off >= 2.0)
            val outline = PreviewColorPreset.STEEL.resolve(scheme, PreviewColorRole.PRIMARY)
            val oc = contrast(outline, grey)
            assertTrue("outline is ${"%.2f".format(oc)}:1 on the shaft grey, needs 3:1", oc >= 3.0)
            // A shaded component is its kind fill laid over the grey, and still reads as shaded.
            listOf(PreviewColorPreset.STEEL, PreviewColorPreset.BRONZE).forEach { preset ->
                val shaded = painted(preset, scheme, grey)
                val sc = contrast(shaded, grey)
                assertTrue("$preset over the shaft grey is ${"%.2f".format(sc)}:1, needs 1.3:1", sc >= 1.3)
            }
        }
    }

    @Test
    fun `dark ground alpha table`() {
        assertEquals(0f, PreviewColorPreset.TRANSPARENT.fillAlpha(true, 0.10f))
        assertEquals(0.34f, PreviewColorPreset.STEEL.fillAlpha(true, 0.10f))
        assertEquals(0.28f, PreviewColorPreset.STAINLESS.fillAlpha(true, 0.10f))
        assertEquals(0.56f, PreviewColorPreset.BRONZE.fillAlpha(true, 0.10f))
        assertEquals(0.20f, PreviewColorPreset.CUSTOM.fillAlpha(true, 0.10f), 1e-6f)
        assertEquals(0.32f, PreviewColorPreset.CUSTOM.fillAlpha(true, 0.16f), 1e-6f)
        assertEquals(0.45f, PreviewColorPreset.CUSTOM.fillAlpha(true, 0.30f), 1e-6f)
    }
}
