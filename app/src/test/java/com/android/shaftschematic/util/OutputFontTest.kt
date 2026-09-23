package com.android.shaftschematic.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The persisted half of Settings → Drawing → "Output font": the shipped face and the tolerant
 * decode that keeps a hand-edited or older stored value from costing a sheet its type.
 *
 * Pure — the enum carries no Android dependency until [OutputFont.typeface] is called, which is
 * why this can run outside Robolectric while the mirror test cannot.
 */
class OutputFontTest {

    @Test
    fun `the shipped face is the historical look`() {
        assertSame(OutputFont.STANDARD, OutputFont.Default)
    }

    @Test
    fun `a stored name decodes to its own face`() {
        assertSame(OutputFont.STANDARD, OutputFont.fromName("STANDARD"))
        assertSame(OutputFont.CONDENSED, OutputFont.fromName("CONDENSED"))
        assertSame(OutputFont.SERIF, OutputFont.fromName("SERIF"))
        assertSame(OutputFont.MONOSPACE, OutputFont.fromName("MONOSPACE"))
    }

    /**
     * Everything unreadable lands on the shipped face — a missing key (a document saved before
     * this pref existed), an empty string, a name from a build that offered more faces, and the
     * lowercase spelling a hand-edited profile might carry.
     */
    @Test
    fun `an unreadable stored value falls back to the shipped face`() {
        assertSame(OutputFont.Default, OutputFont.fromName(null))
        assertSame(OutputFont.Default, OutputFont.fromName(""))
        assertSame(OutputFont.Default, OutputFont.fromName("   "))
        assertSame(OutputFont.Default, OutputFont.fromName("serif"))
        assertSame(OutputFont.Default, OutputFont.fromName("COPPERPLATE"))
    }

    @Test
    fun `every face carries a distinct label`() {
        val labels = OutputFont.entries.map { it.uiLabel() }

        assertEquals(labels.size, labels.toSet().size)
        assertEquals("Standard", OutputFont.STANDARD.uiLabel())
        assertEquals("Condensed", OutputFont.CONDENSED.uiLabel())
        assertEquals("Serif", OutputFont.SERIF.uiLabel())
        assertEquals("Monospace", OutputFont.MONOSPACE.uiLabel())
    }
}
