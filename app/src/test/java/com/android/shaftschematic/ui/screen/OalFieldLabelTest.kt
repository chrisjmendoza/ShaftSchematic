package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The OAL field's label shortens at a large system font: the full label wrapped inside the
 * outline and its first line was clipped (on-device report, 200%). The default scale keeps the
 * full label.
 */
class OalFieldLabelTest {

    @Test
    fun `default and moderate font scales keep the full label`() {
        assertEquals("Overall Length (in)", oalFieldLabel(UnitSystem.INCHES, 1.0f))
        assertEquals("Overall Length (mm)", oalFieldLabel(UnitSystem.MILLIMETERS, OAL_SHORT_LABEL_FONT_SCALE))
    }

    @Test
    fun `a large font scale takes the short label`() {
        assertEquals("OAL (in)", oalFieldLabel(UnitSystem.INCHES, 2.0f))
        assertEquals("OAL (mm)", oalFieldLabel(UnitSystem.MILLIMETERS, 1.5f))
    }

    @Test
    fun `the start card pairs its two fields on a row except on a narrow card at a large font`() {
        assertEquals(true, startCardFieldsShareRow(cardFieldsTwoUp = false, fontScale = 1.0f))
        assertEquals(true, startCardFieldsShareRow(cardFieldsTwoUp = false, fontScale = OAL_SHORT_LABEL_FONT_SCALE))
        assertEquals(false, startCardFieldsShareRow(cardFieldsTwoUp = false, fontScale = 2.0f))
        assertEquals(true, startCardFieldsShareRow(cardFieldsTwoUp = true, fontScale = 2.0f))
    }

    @Test
    fun `the shaft diameter label shortens for the half card`() {
        assertEquals("Shaft Ø (in)", shaftDiaFieldLabel(UnitSystem.INCHES, short = true))
        assertEquals("Shaft diameter (mm)", shaftDiaFieldLabel(UnitSystem.MILLIMETERS, short = false))
    }
}
