package com.android.shaftschematic.util

import androidx.compose.ui.graphics.Color
import com.android.shaftschematic.geom.UNDERCUT_SECTION_FILL_ALPHA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UndercutStyle — the on-screen undercut drawing's user-selectable shading.
 *
 * Pins the invariants the draw sites rely on:
 * - the STANDARD defaults reproduce the historical fixed shades (section fill at
 *   [UNDERCUT_SECTION_FILL_ALPHA], liner at exactly twice that — "section one step
 *   lighter than the liner"),
 * - the section/liner ratio holds at every intensity,
 * - line art empties every fill (the color-removal mode),
 * - persisted-name parsing falls back to defaults instead of crashing.
 */
class UndercutStyleTest {

    @Test
    fun `standard defaults reproduce the historical fixed shades`() {
        val style = UndercutStyle()
        assertEquals(UNDERCUT_SECTION_FILL_ALPHA, style.sectionFill().alpha, 1e-6f)
        assertEquals(2f * UNDERCUT_SECTION_FILL_ALPHA, style.linerFill().alpha, 1e-6f)
        assertEquals(style.sectionFill().alpha, style.bodyFill().alpha, 1e-6f)
    }

    @Test
    fun `section stays one step lighter than the liner at every intensity`() {
        UndercutShadeIntensity.entries.forEach { intensity ->
            val style = UndercutStyle(intensity = intensity)
            assertEquals(
                "intensity $intensity",
                style.linerFill().alpha / 2f,
                style.sectionFill().alpha,
                1e-6f
            )
        }
    }

    @Test
    fun `intensity ladder is strictly ordered`() {
        val light = UndercutStyle(intensity = UndercutShadeIntensity.LIGHT).linerFill().alpha
        val standard = UndercutStyle(intensity = UndercutShadeIntensity.STANDARD).linerFill().alpha
        val dark = UndercutStyle(intensity = UndercutShadeIntensity.DARK).linerFill().alpha
        assertTrue(light < standard)
        assertTrue(standard < dark)
    }

    @Test
    fun `line art empties every fill`() {
        UndercutShadeColor.entries.forEach { color ->
            UndercutShadeIntensity.entries.forEach { intensity ->
                val style = UndercutStyle(lineArt = true, shadeColor = color, intensity = intensity)
                assertEquals(Color.Transparent, style.linerFill())
                assertEquals(Color.Transparent, style.bodyFill())
                assertEquals(Color.Transparent, style.sectionFill())
            }
        }
    }

    @Test
    fun `fills carry the selected base color`() {
        val bronze = UndercutStyle(shadeColor = UndercutShadeColor.BRONZE).linerFill()
        val base = UndercutShadeColor.BRONZE.base
        assertEquals(base.red, bronze.red, 1e-6f)
        assertEquals(base.green, bronze.green, 1e-6f)
        assertEquals(base.blue, bronze.blue, 1e-6f)
    }

    @Test
    fun `shade color parsing falls back to grey`() {
        assertEquals(UndercutShadeColor.GREY, UndercutShadeColor.fromName(null))
        assertEquals(UndercutShadeColor.GREY, UndercutShadeColor.fromName(""))
        assertEquals(UndercutShadeColor.GREY, UndercutShadeColor.fromName("MAGENTA"))
        assertEquals(UndercutShadeColor.BLUE, UndercutShadeColor.fromName("BLUE"))
    }

    @Test
    fun `shade intensity parsing falls back to standard`() {
        assertEquals(UndercutShadeIntensity.STANDARD, UndercutShadeIntensity.fromName(null))
        assertEquals(UndercutShadeIntensity.STANDARD, UndercutShadeIntensity.fromName(""))
        assertEquals(UndercutShadeIntensity.STANDARD, UndercutShadeIntensity.fromName("EXTREME"))
        assertEquals(UndercutShadeIntensity.DARK, UndercutShadeIntensity.fromName("DARK"))
    }
}
