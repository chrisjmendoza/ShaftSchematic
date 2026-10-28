package com.android.shaftschematic.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The sheet canvases' tap/selection affordance is fixed ink, [SheetInk.Selection], not a theme
 * role: a theme accent chosen for its own scheme's surfaces read weakly on the white paper
 * (on-device report: High contrast's amber on the wear overview).
 */
class SheetInkSelectionTest {

    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** The default Light theme draws exactly what it always did. */
    @Test
    fun `selection ink is the historical light primary`() {
        assertEquals(LightColorScheme.primary, SheetInk.Selection)
        assertEquals(LightColorScheme.onPrimary, SheetInk.OnSelection)
    }

    @Test
    fun `selection ink reads on the white sheet and its badge text reads on it`() {
        assertTrue(contrast(SheetInk.Selection, Color.White) >= 4.5)
        assertTrue(contrast(SheetInk.OnSelection, SheetInk.Selection) >= 4.5)
    }
}
