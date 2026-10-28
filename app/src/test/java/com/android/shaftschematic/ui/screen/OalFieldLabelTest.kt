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
}
