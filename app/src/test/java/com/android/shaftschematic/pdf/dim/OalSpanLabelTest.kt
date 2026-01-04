package com.android.shaftschematic.pdf.dim

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OalSpanLabelTest {

    @Test
    fun `OAL top dimension label is plain and unqualified`() {
        val span = oalSpan(oalMm = 1234.0, unit = UnitSystem.MILLIMETERS)

        assertEquals(0.0, span.x1Mm, 0.0)
        assertEquals(1234.0, span.x2Mm, 0.0)

        assertTrue(span.labelTop.startsWith("OAL "))
        assertFalse(span.labelTop.contains("(less", ignoreCase = true))
        assertFalse(span.labelTop.contains("SET-SET", ignoreCase = true))
    }
}
