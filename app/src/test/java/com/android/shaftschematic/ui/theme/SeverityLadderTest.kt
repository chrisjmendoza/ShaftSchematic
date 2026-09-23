package com.android.shaftschematic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The chrome severity ladder — error → caution → neutral — across all four schemes.
 *
 * The caution rung is `tertiaryContainer` — the role the per-card warning chips and the advisory
 * warnings banner sit on. It is easily left unassigned (the high-contrast schemes set
 * `primaryContainer`, `secondaryContainer` and `errorContainer` and once skipped it), and a
 * scheme that skips it silently inherits M3's baseline pale pink — a few degrees of hue from the
 * error rung, which makes an advisory banner read as an error (on-device report).
 *
 * An unset role is exactly what a test can catch, so these assert the assignment itself rather
 * than a fuzzy colour-distance metric.
 */
class SeverityLadderTest {

    /** M3's baseline values for the role — what an unassigned scheme falls back to. */
    private val baselineTertiaryContainerLight = Color(0xFFFFD8E4)
    private val baselineTertiaryContainerDark = Color(0xFF633B48)

    private val schemes = mapOf(
        "light" to LightColorScheme,
        "dark" to DarkColorScheme,
        "hc-light" to HighContrastLightColorScheme,
        "hc-dark" to HighContrastDarkColorScheme,
    )

    /** WCAG relative luminance. */
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    /** WCAG contrast ratio between two opaque colours. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `every scheme assigns the caution rung`() {
        schemes.forEach { (name, scheme) ->
            assertNotEquals(
                "$name still falls back to M3's baseline tertiaryContainer",
                baselineTertiaryContainerLight, scheme.tertiaryContainer,
            )
            assertNotEquals(
                "$name still falls back to M3's baseline tertiaryContainer",
                baselineTertiaryContainerDark, scheme.tertiaryContainer,
            )
        }
    }

    @Test
    fun `caution is never the same colour as error`() {
        schemes.forEach { (name, scheme) ->
            assertNotEquals(
                "$name: the caution and error rungs must be tellable apart",
                scheme.errorContainer, scheme.tertiaryContainer,
            )
        }
    }

    /**
     * Text on the caution rung has to be readable. The high-contrast schemes state a
     * AAA-level figure/ground posture, so they are held to 7:1 and the rest to the AA 4.5:1.
     */
    @Test
    fun `caution text meets its scheme's contrast bar`() {
        schemes.forEach { (name, scheme) ->
            val bar = if (name.startsWith("hc-")) 7.0 else 4.5
            val ratio = contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer)
            assertTrue(
                "$name: onTertiaryContainer on tertiaryContainer is ${"%.2f".format(ratio)}:1, needs $bar:1",
                ratio >= bar,
            )
        }
    }

    /** The high-contrast schemes complete the pattern their other three families follow. */
    @Test
    fun `high contrast reuses its own accent for the caution container`() {
        assertEquals(HcBronzeDark, HighContrastLightColorScheme.tertiaryContainer)
        assertEquals(HcBronzeBright, HighContrastDarkColorScheme.tertiaryContainer)
    }

    /**
     * `tertiary` is NOT part of the ladder — it is the preview-color "Bronze" preset
     * (`PreviewColorPreset.BRONZE` -> `scheme.tertiary`), and `SheetInk.LinerTint` claims to be
     * its historical light value. Recolouring `tertiary` to match the caution rung would make
     * that claim silently false and change the Bronze preset under every saved document.
     */
    @Test
    fun `tertiary stays the Bronze preset and keeps SheetInk truthful`() {
        assertEquals(SheetInk.LinerTint, LightColorScheme.tertiary)
        assertNotEquals(LightColorScheme.tertiaryContainer, LightColorScheme.tertiary)
    }

    /** Guards the ladder's shape: three rungs, all distinct, in every scheme. */
    @Test
    fun `the three rungs are distinct everywhere`() {
        schemes.forEach { (name, scheme) ->
            val rungs: List<Color> = listOf(scheme.errorContainer, scheme.tertiaryContainer, scheme.surface)
            assertEquals("$name: two rungs share a colour", rungs.size, rungs.distinct().size)
        }
    }
}
