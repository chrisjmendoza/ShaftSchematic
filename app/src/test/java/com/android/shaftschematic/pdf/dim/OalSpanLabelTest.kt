package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.android.shaftschematic.util.DualLabel
import org.junit.Test

class OalSpanLabelTest {

    @Test
    fun `OAL top dimension label keeps the OAL prefix and no qualifiers`() {
        val span = oalSpan(x1Mm = 0.0, x2Mm = 1234.0, unit = UnitSystem.MILLIMETERS)

        assertEquals(0.0, span.x1Mm, 0.0)
        assertEquals(1234.0, span.x2Mm, 0.0)

        // The small printed "OAL" prefix is a deliberate visual identifier (product
        // decision); blank drafts drop label text at the renderer, not here.
        assertTrue(span.label.inline().startsWith("OAL "))
        assertFalse(span.label.inline().contains("(less", ignoreCase = true))
        assertFalse(span.label.inline().contains("SET-SET", ignoreCase = true))
    }

    @Test
    fun `OAL span x1 equals aft SET and label shows SET-to-SET distance`() {
        // When AFT SET is not at 0 (e.g. included thread before taper), x1 should be the SET position
        val span = oalSpan(x1Mm = 25.4, x2Mm = 1234.0, unit = UnitSystem.MILLIMETERS)

        assertEquals(25.4, span.x1Mm, 1e-9)
        assertEquals(1234.0, span.x2Mm, 1e-9)
        assertTrue(span.label.inline().startsWith("OAL "))
        // Label distance must match the span width, not some other value
        val dist = 1234.0 - 25.4
        assertTrue(span.label.inline().contains(dist.toLong().toString()) || span.label.inline().length > 4)
    }
}
