package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-term label itself — no Android, no drawing.
 *
 * The property that matters here is that a SINGLE-unit label is indistinguishable from the plain
 * string it replaced, in every rendering: that is what lets a non-dual sheet keep printing
 * byte-identically while the dual paths exist alongside it.
 */
class DualLabelTest {

    @Test
    fun `a single-unit label renders as its plain string in every form`() {
        val l = DualLabel.single("25 9/16\"")
        assertEquals("25 9/16\"", l.inline())
        assertEquals(listOf("25 9/16\""), l.lines())
        assertFalse(l.isDual)
    }

    @Test
    fun `a dual label joins with brackets inline and keeps its terms apart when stacked`() {
        val l = DualLabel("25 9/16\"", "649.3 mm")
        assertTrue(l.isDual)
        assertEquals("25 9/16\" [649.3 mm]", l.inline())
        assertEquals(listOf("25 9/16\"", "649.3 mm"), l.lines())
    }

    @Test
    fun `stacking needs BOTH a second term and a stacked layout`() {
        val dual = DualLabel("1 1/2\"", "38.1 mm")
        val single = DualLabel.single("1 1/2\"")
        assertTrue(dual.setsStacked(stacked = true))
        assertFalse(dual.setsStacked(stacked = false))
        // The case worth pinning: a single-unit label on a stacked sheet must NOT reserve a
        // stack's height for one line of text.
        assertFalse(single.setsStacked(stacked = true))
    }

    @Test
    fun `the layout pref decodes tolerantly and defaults to the shipped inline rendering`() {
        assertEquals(DualUnitLayout.INLINE, DualUnitLayout.Default)
        assertEquals(DualUnitLayout.STACKED, DualUnitLayout.fromName("STACKED"))
        assertEquals(DualUnitLayout.Default, DualUnitLayout.fromName("nonsense"))
        assertEquals(DualUnitLayout.Default, DualUnitLayout.fromName(null))
    }
}
