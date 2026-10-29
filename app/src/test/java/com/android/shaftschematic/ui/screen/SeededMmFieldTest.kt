package com.android.shaftschematic.ui.screen

import com.android.shaftschematic.util.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SeededMmField]: the exact seed until an edit, the typed text after one. */
class SeededMmFieldTest {

    private val seedMm = 35.42422f * 25.4f

    @Test
    fun `an unedited field returns the exact seed, not its display`() {
        val f = SeededMmField(seedMm, "35.424")
        assertEquals(seedMm, f.mm(UnitSystem.INCHES)!!, 0f)
    }

    @Test
    fun `handing back the same text is not an edit`() {
        val f = SeededMmField(seedMm, "35.424")
        f.onText("35.424")   // a blur or Done with nothing typed
        assertFalse(f.edited)
        assertEquals(seedMm, f.mm(UnitSystem.INCHES)!!, 0f)
    }

    @Test
    fun `an edited field parses verbatim, even typed back to the seed's text`() {
        val f = SeededMmField(seedMm, "35.424")
        f.onText("35.42")
        f.onText("35.424")
        assertTrue(f.edited)
        assertEquals(35.424f * 25.4f, f.mm(UnitSystem.INCHES)!!, 1e-3f)
    }

    @Test
    fun `an empty field parses, and a reseed makes it exact again`() {
        val f = SeededMmField.empty()
        assertNull(f.mm(UnitSystem.INCHES))
        f.onText("2")
        assertEquals(50.8f, f.mm(UnitSystem.INCHES)!!, 1e-3f)
        f.reseed(12.3456f, "0.486")
        assertFalse(f.edited)
        assertEquals("0.486", f.text)
        assertEquals(12.3456f, f.mm(UnitSystem.INCHES)!!, 0f)
    }
}
